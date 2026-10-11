import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import { AuthStore } from '../auth/auth.store';
import { ProblemDetail } from '../models';

const LOGIN_URL = `${environment.apiBaseUrl}/auth/login`;

/** Adds the JWT to every API call. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const token = inject(AuthStore).token();
  if (!token || !req.url.startsWith(environment.apiBaseUrl) || req.url === LOGIN_URL) {
    return next(req);
  }
  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/**
 * Central error handling: an expired session sends the user back to the login page, other
 * errors show the backend's ProblemDetail message. The login form handles its own errors.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthStore);
  const snackBar = inject(MatSnackBar);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && req.url !== LOGIN_URL) {
        if (error.status === 401) {
          auth.logout();
          snackBar.open('Votre session a expiré. Veuillez vous reconnecter.', 'OK', { duration: 5000 });
        } else {
          snackBar.open(problemMessage(error), 'Fermer', { duration: 6000 });
        }
      }
      return throwError(() => error);
    }),
  );
};

export function problemMessage(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Le serveur est injoignable. Vérifiez votre connexion.';
  }
  if (error.status === 403) {
    return 'Vous n’avez pas les droits nécessaires pour cette action.';
  }
  const problem = error.error as Partial<ProblemDetail> | null;
  return problem?.detail ?? problem?.title ?? 'Une erreur inattendue est survenue.';
}
