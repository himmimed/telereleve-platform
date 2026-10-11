import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { Role } from '../models';
import { AuthStore } from './auth.store';

/** Only signed-in users; others go to the login page, which brings them back afterwards. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthStore);
  if (auth.isAuthenticated()) {
    return true;
  }
  return inject(Router).createUrlTree(['/connexion'], { queryParams: { retour: state.url } });
};

/**
 * Restricts a route to the roles listed in `data.roles`. The backend enforces the same rules;
 * this guard only avoids showing pages the user cannot use.
 */
export const roleGuard: CanActivateFn = (route) => {
  const auth = inject(AuthStore);
  const roles = route.data['roles'] as Role[] | undefined;
  if (auth.hasAnyRole(roles)) {
    return true;
  }
  return inject(Router).createUrlTree(['/tableau-de-bord']);
};

/** The login page is pointless once signed in. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthStore);
  return auth.isAuthenticated() ? inject(Router).createUrlTree(['/tableau-de-bord']) : true;
};
