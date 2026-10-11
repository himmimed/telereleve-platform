import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, map } from 'rxjs';
import { environment } from '../../../environments/environment';
import { LoginResponse, Role } from '../models';

interface Session {
  token: string;
  role: Role;
  fullName: string;
  username: string;
}

/**
 * Holds the authenticated session.
 * The JWT lives in memory only (a signal), never in localStorage, so a script injected into the
 * page cannot read it from storage. The trade-off is that reloading the page signs the user out.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(null);

  readonly isAuthenticated = computed(() => this.session() !== null);
  readonly token = computed(() => this.session()?.token ?? null);
  readonly role = computed(() => this.session()?.role ?? null);
  readonly fullName = computed(() => this.session()?.fullName ?? '');
  readonly username = computed(() => this.session()?.username ?? '');

  login(username: string, password: string): Observable<void> {
    return this.http
      .post<LoginResponse>(`${environment.apiBaseUrl}/auth/login`, { username, password })
      .pipe(
        map((response) => {
          this.session.set({ ...response, username });
        }),
      );
  }

  logout(redirect = true): void {
    this.session.set(null);
    if (redirect) {
      void this.router.navigate(['/connexion']);
    }
  }

  hasAnyRole(roles: readonly Role[] | undefined): boolean {
    if (!roles || roles.length === 0) {
      return this.isAuthenticated();
    }
    const role = this.role();
    return role !== null && roles.includes(role);
  }
}
