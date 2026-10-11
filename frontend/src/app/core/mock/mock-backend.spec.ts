// The mock API must behave like the specified backend, otherwise the UI would be built against a
// contract the real API does not honour. These tests pin the permission rules and data invariants.
import { HttpClient, HttpErrorResponse, provideHttpClient, withInterceptors } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AuthStore } from '../auth/auth.store';
import { authInterceptor } from '../http/interceptors';
import { Alert, Consumption, Page, Role, TrendPoint } from '../models';
import { mockBackendInterceptor } from './mock-backend.interceptor';

const USERNAME: Record<Role, string> = { ADMIN: 'admin', OPERATOR: 'operateur', TECHNICIAN: 'technicien' };

describe('mock backend', () => {
  let http: HttpClient;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor, mockBackendInterceptor])),
      ],
    });
    http = TestBed.inject(HttpClient);
  });

  const signInAs = (role: Role) => firstValueFrom(TestBed.inject(AuthStore).login(USERNAME[role], 'demo1234'));

  async function statusOf(request: Promise<unknown>): Promise<number> {
    try {
      await request;
      return 200;
    } catch (error) {
      return (error as HttpErrorResponse).status;
    }
  }

  it('rejects a wrong password with 401', async () => {
    const status = await statusOf(firstValueFrom(TestBed.inject(AuthStore).login('admin', 'wrong')));
    expect(status).toBe(401);
  });

  it('returns 401 to unauthenticated calls', async () => {
    expect(await statusOf(firstValueFrom(http.get('/api/meters')))).toBe(401);
  });

  it('returns 403 when a technician creates a meter', async () => {
    await signInAs('TECHNICIAN');
    const status = await statusOf(firstValueFrom(http.post('/api/meters', { serial: 'CEAC-E-000999' })));
    expect(status).toBe(403);
  });

  it('only shows a technician the alerts assigned to them', async () => {
    await signInAs('TECHNICIAN');
    const page = await firstValueFrom(http.get<Page<Alert>>('/api/alerts', { params: { size: 100 } }));
    expect(page.content.length).toBeGreaterThan(0);
    expect(page.content.every((a) => a.assignedTo?.fullName === 'Hamza Tazi')).toBe(true);
  });

  it('keeps at most one open alert per meter and rule', async () => {
    await signInAs('OPERATOR');
    const page = await firstValueFrom(http.get<Page<Alert>>('/api/alerts', { params: { size: 500 } }));
    const open = page.content.filter((a) => a.status !== 'RESOLVED').map((a) => `${a.meterId}:${a.ruleCode}`);
    expect(new Set(open).size).toBe(open.length);
  });

  it('never returns negative consumption, even across gaps', async () => {
    await signInAs('OPERATOR');
    const meters = await firstValueFrom(http.get<Page<{ id: string }>>('/api/meters', { params: { size: 100 } }));
    const now = new Date();
    const from = new Date(now.getTime() - 7 * 86_400_000).toISOString();
    // All meters, including the silent and zero-consumption ones (gaps in their data).
    const results = await Promise.all(
      meters.content.map((meter) =>
        firstValueFrom(
          http.get<Consumption>(`/api/meters/${meter.id}/consumption`, {
            params: { from, to: now.toISOString(), step: 'day' },
          }),
        ),
      ),
    );
    expect(results.length).toBe(50);
    expect(results.every((c) => c.points.every((p) => p.value >= 0))).toBe(true);
  });

  it('splits the network trend by energy', async () => {
    await signInAs('OPERATOR');
    const trend = await firstValueFrom(http.get<TrendPoint[]>('/api/dashboard/consumption-trend'));
    expect(trend.length).toBeGreaterThanOrEqual(24);
    expect(trend.every((p) => p.electricityKwh >= 0 && p.waterM3 >= 0)).toBe(true);
  });
});
