import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { AuthStore } from '../auth/auth.store';
import { authInterceptor, problemMessage } from './interceptors';

describe('authInterceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    TestBed.inject(AuthStore).login('operateur', 'secret').subscribe();
    backend.expectOne('/api/auth/login').flush({ token: 'jwt-123', role: 'OPERATOR', fullName: 'Salma Bennani' });
  });

  it('adds the bearer token to API calls', () => {
    http.get('/api/meters').subscribe();
    expect(backend.expectOne('/api/meters').request.headers.get('Authorization')).toBe('Bearer jwt-123');
  });

  it('does not send the token to other hosts', () => {
    http.get('https://tile.openstreetmap.org/1/1/1.png').subscribe();
    expect(backend.expectOne('https://tile.openstreetmap.org/1/1/1.png').request.headers.has('Authorization')).toBe(false);
  });

  it('stops sending the token after logout', () => {
    TestBed.inject(AuthStore).logout(false);
    http.get('/api/meters').subscribe();
    expect(backend.expectOne('/api/meters').request.headers.has('Authorization')).toBe(false);
  });
});

describe('problemMessage', () => {
  it('shows the RFC 7807 detail sent by the backend', () => {
    const error = new HttpErrorResponse({ status: 409, error: { title: 'Conflit', status: 409, detail: 'Le numéro existe déjà.' } });
    expect(problemMessage(error)).toBe('Le numéro existe déjà.');
  });

  it('explains a forbidden action in French', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 403 }))).toContain('droits');
  });

  it('explains an unreachable server', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toContain('injoignable');
  });
});
