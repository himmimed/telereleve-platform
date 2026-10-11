import { HttpErrorResponse } from '@angular/common/http';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';
import type * as Leaflet from 'leaflet';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../../core/auth/auth.store';
import { ROLE_LABEL } from '../../core/labels';
import { DEMO_ACCOUNTS } from '../../core/mock/mock-db';
import { ProblemDetail } from '../../core/models';
import { ThemeService } from '../../core/theme.service';
import { FES_CENTER, baseLayer } from '../../shared/map-tiles';

@Component({
  selector: 'app-login-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './login.page.html',
  styleUrl: './login.page.scss',
})
export class LoginPage {
  /** Page to return to after signing in (set by the auth guard). */
  readonly retour = input<string>();

  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
  });
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly showPassword = signal(false);
  /** Demo accounts are only offered with the simulated API, never with the real backend. */
  protected readonly demoAccounts = environment.useMockApi
    ? DEMO_ACCOUNTS.slice(0, 3).map((a) => ({ ...a, roleLabel: ROLE_LABEL[a.role] }))
    : [];

  private readonly mapElement = viewChild.required<ElementRef<HTMLElement>>('map');
  private readonly leaflet = signal<{ L: typeof Leaflet; map: Leaflet.Map } | null>(null);
  private tiles?: Leaflet.TileLayer;

  constructor() {
    // Background map of Fès: Leaflet is loaded lazily, after the form is on screen.
    afterNextRender(async () => {
      const L = await import('leaflet');
      const map = L.map(this.mapElement().nativeElement, {
        center: FES_CENTER,
        zoom: 13,
        zoomControl: false,
        dragging: false,
        scrollWheelZoom: false,
        doubleClickZoom: false,
        boxZoom: false,
        keyboard: false,
        touchZoom: false,
      });
      this.leaflet.set({ L, map });
    });

    // Tiles follow the light / dark theme.
    effect(() => {
      const leaflet = this.leaflet();
      const dark = this.theme.isDark();
      if (leaflet) {
        this.tiles?.remove();
        this.tiles = baseLayer(leaflet.L, dark).addTo(leaflet.map);
      }
    });

    inject(DestroyRef).onDestroy(() => this.leaflet()?.map.remove());
  }

  protected fill(username: string, password: string): void {
    this.form.setValue({ username, password });
    this.error.set(null);
  }

  protected submit(): void {
    if (this.form.invalid || this.submitting()) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);
    this.auth.login(username.trim(), password).subscribe({
      next: () => {
        const target = this.retour();
        void this.router.navigateByUrl(target?.startsWith('/') ? target : '/tableau-de-bord');
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        const problem = err.error as Partial<ProblemDetail> | null;
        this.error.set(
          err.status === 0
            ? 'Le serveur est injoignable. Réessayez dans un instant.'
            : (problem?.detail ?? 'Identifiant ou mot de passe incorrect.'),
        );
      },
    });
  }
}
