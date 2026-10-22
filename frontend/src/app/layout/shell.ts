import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, ElementRef, computed, effect, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, map, startWith, switchMap, timer } from 'rxjs';
import { environment } from '../../environments/environment';
import { DashboardApi } from '../core/api/api.services';
import { AuthStore } from '../core/auth/auth.store';
import { ROLE_LABEL } from '../core/labels';
import { Role } from '../core/models';
import { ThemeService } from '../core/theme.service';

interface NavItem {
  label: string;
  icon: string;
  path: string;
  roles?: Role[];
  badge?: boolean;
}

const NAV: NavItem[] = [
  { label: 'Tableau de bord', icon: 'ph-squares-four', path: '/tableau-de-bord' },
  { label: 'Carte', icon: 'ph-map-trifold', path: '/carte' },
  { label: 'Compteurs', icon: 'ph-gauge', path: '/compteurs', roles: ['ADMIN', 'OPERATOR'] },
  { label: 'Alertes', icon: 'ph-bell', path: '/alertes', badge: true },
  { label: 'Administration', icon: 'ph-gear-six', path: '/administration', roles: ['ADMIN'] },
];

@Component({
  selector: 'app-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatButtonModule, MatMenuModule, MatTooltipModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
})
export class Shell {
  protected readonly auth = inject(AuthStore);
  protected readonly theme = inject(ThemeService);
  protected readonly mockMode = environment.useMockApi;
  private readonly dashboardApi = inject(DashboardApi);

  /** Below 1024 px the navigation links fold into a sheet under the top bar. */
  protected readonly compact = toSignal(
    inject(BreakpointObserver)
      .observe('(max-width: 1023.98px)')
      .pipe(map((state) => state.matches)),
    { initialValue: false },
  );
  protected readonly menuOpen = signal(false);

  protected readonly nav = computed(() => NAV.filter((item) => this.auth.hasAnyRole(item.roles)));
  protected readonly roleLabel = computed(() => {
    const role = this.auth.role();
    return role ? ROLE_LABEL[role] : '';
  });
  protected readonly initials = computed(() =>
    this.auth
      .fullName()
      .split(' ')
      .filter(Boolean)
      .map((part) => part[0])
      .slice(0, 2)
      .join('')
      .toUpperCase(),
  );

  /** Open alerts next to the Alertes link, refreshed every minute (technicians see their own list instead). */
  protected readonly openAlerts = toSignal(
    timer(0, 60_000).pipe(
      filter(() => this.auth.role() !== 'TECHNICIAN'),
      switchMap(() => this.dashboardApi.stats()),
      map((stats) => stats.openAlerts.CRITICAL + stats.openAlerts.HIGH + stats.openAlerts.MEDIUM),
    ),
    { initialValue: 0 },
  );

  private readonly links = viewChild<ElementRef<HTMLElement>>('links');
  /**
   * The marker under the current link (soft pill and lime diamond): one element that slides from link to
   * link, measured from the DOM. `animate` stays off for the first placement so it does not slide in from
   * the left edge on load.
   */
  protected readonly marker = signal({ x: 0, w: 0, shown: false, animate: false });

  constructor() {
    const router = inject(Router);
    // Close the narrow-screen menu after navigating, and move the marker once the active link has updated.
    router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe(() => {
        this.menuOpen.set(false);
        this.afterFrame(() => this.measureMarker(true));
      });

    // Re-measure whenever the links appear or any of them changes size (a web font arriving, a resize): the
    // centred links shift when a neighbour grows, and the marker must follow.
    effect((onCleanup) => {
      const element = this.links()?.nativeElement;
      this.nav();
      if (!element) {
        return;
      }
      this.afterFrame(() => this.measureMarker());
      void document.fonts?.ready.then(() => this.measureMarker());
      if (typeof ResizeObserver === 'undefined') {
        return;
      }
      const observer = new ResizeObserver(() => this.measureMarker());
      observer.observe(element);
      element.querySelectorAll('a.link').forEach((link) => observer.observe(link));
      onCleanup(() => observer.disconnect());
    });
  }

  private afterFrame(task: () => void): void {
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(task);
    }
  }

  /**
   * `travel` is true after a page change. Once the marker has travelled it keeps its transition, so a
   * later re-measure (resize, font) glides to the new place instead of jumping, and never cuts a glide short.
   */
  private measureMarker(travel = false): void {
    const active = this.links()?.nativeElement.querySelector<HTMLElement>('a.link.active');
    if (!active) {
      this.marker.update((m) => ({ ...m, shown: false }));
      return;
    }
    // Fractional pixels, not offsetLeft / offsetWidth: those round, and the pill would sit up to half a pixel off.
    const nav = this.links()!.nativeElement.getBoundingClientRect();
    const box = active.getBoundingClientRect();
    const x = Math.round((box.left - nav.left) * 100) / 100;
    const w = Math.round(box.width * 100) / 100;
    this.marker.update((m) => (m.x === x && m.w === w && m.shown ? m : { x, w, shown: true, animate: m.shown && (travel || m.animate) }));
  }

  protected logout(): void {
    this.auth.logout();
  }
}
