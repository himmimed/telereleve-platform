import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRouteSnapshot, Router, RouterStateSnapshot, UrlTree, provideRouter } from '@angular/router';
import { Role } from '../models';
import { authGuard, roleGuard } from './auth.guards';
import { AuthStore } from './auth.store';

describe('auth guards', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  function signInAs(role: Role): void {
    TestBed.inject(AuthStore).login('user', 'secret').subscribe();
    http.expectOne('/api/auth/login').flush({ token: 't', role, fullName: 'Test' });
  }

  function runRoleGuard(roles: Role[]): boolean | UrlTree {
    const route = { data: { roles } } as unknown as ActivatedRouteSnapshot;
    return TestBed.runInInjectionContext(() => roleGuard(route, {} as RouterStateSnapshot)) as boolean | UrlTree;
  }

  it('sends anonymous users to the login page and remembers where they were going', () => {
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/alertes' } as RouterStateSnapshot),
    ) as UrlTree;

    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/connexion?retour=%2Falertes');
  });

  it('lets a signed-in user through', () => {
    signInAs('TECHNICIAN');
    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as ActivatedRouteSnapshot, { url: '/alertes' } as RouterStateSnapshot),
    );
    expect(result).toBe(true);
  });

  it('allows an operator on the meter list', () => {
    signInAs('OPERATOR');
    expect(runRoleGuard(['ADMIN', 'OPERATOR'])).toBe(true);
  });

  it('redirects a technician away from the meter list', () => {
    signInAs('TECHNICIAN');
    const result = runRoleGuard(['ADMIN', 'OPERATOR']) as UrlTree;
    expect(TestBed.inject(Router).serializeUrl(result)).toBe('/tableau-de-bord');
  });

  it('keeps administration for administrators only', () => {
    signInAs('OPERATOR');
    expect(runRoleGuard(['ADMIN'])).not.toBe(true);
  });
});
