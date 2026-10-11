import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { MAT_SNACK_BAR_DEFAULT_OPTIONS } from '@angular/material/snack-bar';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling } from '@angular/router';
import { environment } from '../environments/environment';
import { routes } from './app.routes';
import { authInterceptor, errorInterceptor } from './core/http/interceptors';
import { mockBackendInterceptor } from './core/mock/mock-backend.interceptor';
import { FrenchPaginatorIntl } from './shared/french-paginator';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top' }),
    ),
    provideHttpClient(
      withInterceptors([
        authInterceptor,
        errorInterceptor,
        // Last in the chain so it sees the Authorization header, exactly like the real backend.
        ...(environment.useMockApi ? [mockBackendInterceptor] : []),
      ]),
    ),
    { provide: LOCALE_ID, useValue: 'fr' },
    { provide: MatPaginatorIntl, useClass: FrenchPaginatorIntl },
    {
      provide: MAT_SNACK_BAR_DEFAULT_OPTIONS,
      useValue: { horizontalPosition: 'end', verticalPosition: 'bottom', duration: 4000 },
    },
  ],
};
