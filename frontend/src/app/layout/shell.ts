import { BreakpointObserver } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatIconModule, MatButtonModule, MatTooltipModule],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  host: {
    '[class.rail]': 'layout() === "rail"',
    '[class.mobile]': 'layout() === "mobile"',
    '[class.drawer-open]': 'drawerOpen()',
  },
})
export class Shell {
  protected readonly auth = inject(AuthStore);
  protected readonly theme = inject(ThemeService);
  protected readonly mockMode = environment.useMockApi;
  private readonly dashboardApi = inject(DashboardApi);

  /** full sidebar on desktop, icon rail on tablet, drawer on phone. */
  protected readonly layout = toSignal(
    inject(BreakpointObserver)
      .observe(['(min-width: 1200px)', '(min-width: 768px)'])
      .pipe(
        map((state) =>
          state.breakpoints['(min-width: 1200px)'] ? 'full' : state.breakpoints['(min-width: 768px)'] ? 'rail' : 'mobile',
        ),
      ),
    { initialValue: 'full' },
  );
  protected readonly drawerOpen = signal(false);

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

  /** Open alerts in the sidebar, refreshed every minute (technicians see their own list instead). */
  protected readonly openAlerts = toSignal(
    timer(0, 60_000).pipe(
      filter(() => this.auth.role() !== 'TECHNICIAN'),
      switchMap(() => this.dashboardApi.stats()),
      map((stats) => stats.openAlerts.CRITICAL + stats.openAlerts.HIGH + stats.openAlerts.MEDIUM),
    ),
    { initialValue: 0 },
  );

  constructor() {
    // Close the phone drawer after navigating.
    inject(Router)
      .events.pipe(
        filter((event) => event instanceof NavigationEnd),
        startWith(null),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.drawerOpen.set(false));
  }

  protected logout(): void {
    this.auth.logout();
  }
}
