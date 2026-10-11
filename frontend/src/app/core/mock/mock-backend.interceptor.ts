// Answers /api calls in the browser while the Spring Boot API is being built (environment.useMockApi).
// It follows the REST contract of the specification, including role permissions and RFC 7807
// errors, so switching to the real backend only means turning the flag off.
import { HttpErrorResponse, HttpEvent, HttpInterceptorFn, HttpRequest, HttpResponse } from '@angular/common/http';
import { Observable, delay, mergeMap, of, throwError, timer } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Alert,
  AlertUpdate,
  ConsumptionStep,
  DashboardStats,
  MeterRequest,
  Page,
  ProblemDetail,
  Role,
  RuleCode,
  RuleConfig,
  Severity,
  User,
  UserRequest,
} from '../models';
import { DAY, MockDb, alignBucket } from './mock-db';

const db = new MockDb();
const API = environment.apiBaseUrl;

interface Caller {
  username: string;
  role: Role;
  id: string;
}

type Handler = (req: HttpRequest<unknown>, match: RegExpMatchArray, caller: Caller | null) => Reply;
type Reply = { status: number; body: unknown };

const ALL: Role[] = ['ADMIN', 'OPERATOR', 'TECHNICIAN'];

const ok = (body: unknown): Reply => ({ status: 200, body });
const problem = (status: number, title: string, detail?: string): Reply => ({
  status,
  body: { type: 'about:blank', title, status, detail } satisfies ProblemDetail,
});

function page<T>(items: T[], pageIndex: number, size: number): Page<T> {
  const start = pageIndex * size;
  return {
    content: items.slice(start, start + size),
    page: pageIndex,
    size,
    totalElements: items.length,
    totalPages: Math.max(1, Math.ceil(items.length / size)),
  };
}

function num(req: HttpRequest<unknown>, name: string, fallback: number): number {
  const value = req.params.get(name);
  return value === null ? fallback : Number(value);
}

function time(req: HttpRequest<unknown>, name: string, fallback: number): number {
  const value = req.params.get(name);
  return value === null ? fallback : Date.parse(value);
}

function tokenFor(username: string): string {
  return `mock.${btoa(username)}.signature`;
}

function callerOf(req: HttpRequest<unknown>): Caller | null {
  const header = req.headers.get('Authorization');
  const token = header?.startsWith('Bearer ') ? header.slice(7) : null;
  const username = token?.startsWith('mock.') ? atob(token.split('.')[1]) : null;
  const user = db.users.find((u) => u.username === username && u.enabled);
  return user ? { username: user.username, role: user.role, id: user.id } : null;
}

function publicUser({ password: _password, ...user }: User & { password: string }): User {
  return user;
}

function validateMeter(body: MeterRequest, currentId?: string): Reply | null {
  if (!/^CEAC-[EW]-\d{6}$/.test(body.serial ?? '')) {
    return problem(400, 'Requête invalide', 'Le numéro de série doit respecter le format CEAC-E-000123.');
  }
  if (db.meters.some((m) => m.serial === body.serial && m.id !== currentId)) {
    return problem(409, 'Conflit', `Le numéro de série ${body.serial} existe déjà.`);
  }
  return null;
}

// ------------------------------------------------------------------------------------------------
// Routes: [method, path pattern, allowed roles (null = public), handler]
// ------------------------------------------------------------------------------------------------

const routes: [string, RegExp, Role[] | null, Handler][] = [
  ['POST', /^\/auth\/login$/, null, (req) => {
    const { username, password } = req.body as { username: string; password: string };
    const user = db.users.find((u) => u.username === username);
    if (!user || user.password !== password) {
      return problem(401, 'Authentification refusée', 'Identifiant ou mot de passe incorrect.');
    }
    if (!user.enabled) {
      return problem(401, 'Authentification refusée', 'Ce compte est désactivé.');
    }
    return ok({ token: tokenFor(user.username), role: user.role, fullName: user.fullName });
  }],

  ['GET', /^\/meters\/map$/, ALL, () => {
    const now = Date.now();
    return ok(
      db.meters
        .filter((m) => m.status === 'ACTIVE')
        .map((m) => ({
          id: m.id,
          serial: m.serial,
          type: m.type,
          clientName: m.clientName,
          lat: m.latitude,
          lng: m.longitude,
          status: db.mapStatus(m, now),
        })),
    );
  }],

  ['GET', /^\/meters$/, ['ADMIN', 'OPERATOR'], (req) => {
    const now = Date.now();
    const type = req.params.get('type');
    const status = req.params.get('status');
    const search = req.params.get('search')?.trim().toLowerCase();
    const items = db.meters
      .filter((m) => !type || m.type === type)
      .filter((m) => !status || m.status === status)
      .filter(
        (m) =>
          !search ||
          [m.serial, m.clientName, m.address].some((field) => field.toLowerCase().includes(search)),
      )
      .map((m) => db.toMeter(m, now));
    return ok(page(items, num(req, 'page', 0), num(req, 'size', 20)));
  }],

  ['POST', /^\/meters$/, ['ADMIN'], (req) => {
    const body = req.body as MeterRequest;
    const invalid = validateMeter(body);
    return invalid ?? { status: 201, body: db.toMeter(db.createMeter(body), Date.now()) };
  }],

  ['GET', /^\/meters\/([\w-]+)\/consumption$/, ['ADMIN', 'OPERATOR'], (req, [, id]) => {
    const meter = db.findMeter(id);
    if (!meter) {
      return problem(404, 'Introuvable', 'Ce compteur n’existe pas.');
    }
    const now = Date.now();
    const step = (req.params.get('step') ?? 'day') as ConsumptionStep;
    const points = db
      .buckets(meter, time(req, 'from', now - DAY), time(req, 'to', now), step, now)
      .map((p) => ({ bucket: new Date(p.bucket).toISOString(), value: Math.round(p.value * 1000) / 1000 }));
    return ok({
      meterId: id,
      unit: meter.type === 'ELECTRICITY' ? 'kWh' : 'm3',
      step,
      total: Math.round(points.reduce((sum, p) => sum + p.value, 0) * 1000) / 1000,
      points,
    });
  }],

  ['GET', /^\/meters\/([\w-]+)\/export\.csv$/, ['ADMIN', 'OPERATOR'], (req, [, id]) => {
    const meter = db.findMeter(id);
    if (!meter) {
      return problem(404, 'Introuvable', 'Ce compteur n’existe pas.');
    }
    const now = Date.now();
    const readings = db.readings(meter, time(req, 'from', now - DAY), time(req, 'to', now), now);
    const lines = readings.map((r) =>
      [r.timestamp, r.index.toFixed(3), r.voltage?.toFixed(1) ?? '', r.signalStrength].join(';'),
    );
    const csv = ['timestamp;index;voltage;signal_strength', ...lines].join('\n');
    return ok(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  }],

  ['GET', /^\/meters\/([\w-]+)$/, ['ADMIN', 'OPERATOR'], (_req, [, id]) => {
    const meter = db.findMeter(id);
    return meter ? ok(db.details(meter, Date.now())) : problem(404, 'Introuvable', 'Ce compteur n’existe pas.');
  }],

  ['PUT', /^\/meters\/([\w-]+)$/, ['ADMIN'], (req, [, id]) => {
    const meter = db.findMeter(id);
    if (!meter) {
      return problem(404, 'Introuvable', 'Ce compteur n’existe pas.');
    }
    const body = req.body as MeterRequest;
    const invalid = validateMeter(body, id);
    if (invalid) {
      return invalid;
    }
    db.updateMeter(meter, body);
    return ok(db.toMeter(meter, Date.now()));
  }],

  ['GET', /^\/alerts$/, ALL, (req, _match, caller) => {
    const filters = {
      status: req.params.get('status'),
      severity: req.params.get('severity'),
      ruleCode: req.params.get('ruleCode'),
      meterId: req.params.get('meterId'),
    };
    const items = db.alerts
      .filter((a) => caller?.role !== 'TECHNICIAN' || a.assignedTo?.id === caller.id)
      .filter((a) => !filters.status || a.status === filters.status)
      .filter((a) => !filters.severity || a.severity === filters.severity)
      .filter((a) => !filters.ruleCode || a.ruleCode === filters.ruleCode)
      .filter((a) => !filters.meterId || a.meterId === filters.meterId)
      .sort((a, b) => sortAlerts(a, b));
    return ok(page(items, num(req, 'page', 0), num(req, 'size', 20)));
  }],

  ['PATCH', /^\/alerts\/([\w-]+)$/, ['OPERATOR', 'TECHNICIAN'], (req, [, id], caller) => {
    const alert = db.alerts.find((a) => a.id === id);
    if (!alert) {
      return problem(404, 'Introuvable', 'Cette alerte n’existe pas.');
    }
    const update = req.body as AlertUpdate;
    if (caller?.role === 'TECHNICIAN') {
      if (alert.assignedTo?.id !== caller.id) {
        return problem(403, 'Accès refusé', 'Cette alerte ne vous est pas assignée.');
      }
      if (update.assignedTo !== undefined) {
        return problem(403, 'Accès refusé', 'Seul un opérateur peut modifier l’assignation.');
      }
    }
    if (update.assignedTo !== undefined) {
      const user = db.users.find((u) => u.id === update.assignedTo);
      if (update.assignedTo !== null && user?.role !== 'TECHNICIAN') {
        return problem(400, 'Requête invalide', 'Une alerte ne peut être assignée qu’à un technicien.');
      }
      alert.assignedTo = db.summary(user);
    }
    if (update.status) {
      alert.status = update.status;
      alert.resolvedAt = update.status === 'RESOLVED' ? new Date().toISOString() : null;
    }
    return ok(alert);
  }],

  ['GET', /^\/dashboard\/stats$/, ALL, () => {
    const now = Date.now();
    const active = db.meters.filter((m) => m.status === 'ACTIVE');
    const midnight = alignBucket(now, 'day');
    const openAlerts: Record<Severity, number> = { CRITICAL: 0, HIGH: 0, MEDIUM: 0 };
    db.alerts.filter((a) => a.status !== 'RESOLVED').forEach((a) => openAlerts[a.severity]++);
    const total = (type: string) =>
      active.filter((m) => m.type === type).reduce((sum, m) => sum + db.sum(m, midnight, now, now), 0);
    return ok({
      activeMeters: active.length,
      silentMeters: active.filter((m) => db.isSilent(m, now)).length,
      openAlerts,
      consumptionToday: {
        electricityKwh: Math.round(total('ELECTRICITY') * 10) / 10,
        waterM3: Math.round(total('WATER') * 100) / 100,
      },
    } satisfies DashboardStats);
  }],

  ['GET', /^\/dashboard\/consumption-trend$/, ALL, (req) => {
    const now = Date.now();
    const step = (req.params.get('step') ?? 'hour') as ConsumptionStep;
    const from = time(req, 'from', now - DAY);
    const to = time(req, 'to', now);
    const of = (type: string) => db.meters.filter((m) => m.type === type && m.status === 'ACTIVE');
    const electricity = db.buckets(of('ELECTRICITY'), from, to, step, now);
    const water = db.buckets(of('WATER'), from, to, step, now);
    return ok(
      electricity.map((p, i) => ({
        bucket: new Date(p.bucket).toISOString(),
        electricityKwh: Math.round(p.value * 10) / 10,
        waterM3: Math.round(water[i].value * 100) / 100,
      })),
    );
  }],

  ['GET', /^\/rules$/, ['ADMIN'], () => ok(db.rules)],

  ['PUT', /^\/rules\/(A[1-5])$/, ['ADMIN'], (req, [, code]) => {
    const rule = db.rules.find((r) => r.ruleCode === (code as RuleCode));
    const body = req.body as RuleConfig;
    if (!rule) {
      return problem(404, 'Introuvable', 'Cette règle n’existe pas.');
    }
    if ((body.threshold !== null && body.threshold < 0) || (body.windowHours !== null && body.windowHours < 1)) {
      return problem(400, 'Requête invalide', 'Les seuils doivent être positifs.');
    }
    Object.assign(rule, { enabled: body.enabled, threshold: body.threshold, windowHours: body.windowHours });
    return ok(rule);
  }],

  ['GET', /^\/users\/technicians$/, ['ADMIN', 'OPERATOR'], () =>
    ok(db.users.filter((u) => u.role === 'TECHNICIAN' && u.enabled).map((u) => db.summary(u))),
  ],

  ['GET', /^\/users$/, ['ADMIN'], () => ok(db.users.map(publicUser))],

  ['POST', /^\/users$/, ['ADMIN'], (req) => {
    const body = req.body as UserRequest;
    if (db.users.some((u) => u.username === body.username)) {
      return problem(409, 'Conflit', `L’identifiant ${body.username} est déjà utilisé.`);
    }
    if (!body.password || body.password.length < 8) {
      return problem(400, 'Requête invalide', 'Le mot de passe doit contenir au moins 8 caractères.');
    }
    const user = db.createUser({ ...body, password: body.password });
    return { status: 201, body: publicUser(user) };
  }],

  ['PUT', /^\/users\/([\w-]+)$/, ['ADMIN'], (req, [, id], caller) => {
    const user = db.users.find((u) => u.id === id);
    if (!user) {
      return problem(404, 'Introuvable', 'Cet utilisateur n’existe pas.');
    }
    const body = req.body as UserRequest;
    if (db.users.some((u) => u.username === body.username && u.id !== id)) {
      return problem(409, 'Conflit', `L’identifiant ${body.username} est déjà utilisé.`);
    }
    if (user.id === caller?.id && (!body.enabled || body.role !== 'ADMIN')) {
      return problem(400, 'Requête invalide', 'Vous ne pouvez pas retirer vos propres droits d’administration.');
    }
    const { password, ...rest } = body;
    Object.assign(user, rest, password ? { password } : {});
    return ok(publicUser(user));
  }],

  ['DELETE', /^\/users\/([\w-]+)$/, ['ADMIN'], (_req, [, id], caller) => {
    if (id === caller?.id) {
      return problem(400, 'Requête invalide', 'Vous ne pouvez pas supprimer votre propre compte.');
    }
    const index = db.users.findIndex((u) => u.id === id);
    if (index < 0) {
      return problem(404, 'Introuvable', 'Cet utilisateur n’existe pas.');
    }
    db.users.splice(index, 1);
    db.alerts.filter((a) => a.assignedTo?.id === id).forEach((a) => (a.assignedTo = null));
    return { status: 204, body: null };
  }],
];

const SEVERITY_ORDER: Record<Severity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2 };

/** Open alerts first, then by severity, then most recent. */
function sortAlerts(a: Alert, b: Alert): number {
  const open = Number(a.status === 'RESOLVED') - Number(b.status === 'RESOLVED');
  if (open !== 0) {
    return open;
  }
  const severity = a.status === 'RESOLVED' ? 0 : SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity];
  return severity !== 0 ? severity : Date.parse(b.detectedAt) - Date.parse(a.detectedAt);
}

function dispatch(req: HttpRequest<unknown>): Reply {
  const path = req.url.slice(API.length).split('?')[0];
  for (const [method, pattern, roles, handler] of routes) {
    const match = path.match(pattern);
    if (method !== req.method || !match) {
      continue;
    }
    const caller = callerOf(req);
    if (roles !== null && !caller) {
      return problem(401, 'Non authentifié', 'Authentification requise.');
    }
    if (roles !== null && caller && !roles.includes(caller.role)) {
      return problem(403, 'Accès refusé', 'Vous n’avez pas les droits nécessaires pour cette action.');
    }
    return handler(req, match, caller);
  }
  return problem(404, 'Introuvable', `Aucune ressource ${req.method} ${path}.`);
}

/** Simulated network latency, so loading states are visible as they would be in production. */
function latency(): number {
  return 180 + Math.random() * 320;
}

export const mockBackendInterceptor: HttpInterceptorFn = (req, next): Observable<HttpEvent<unknown>> => {
  if (!req.url.startsWith(API)) {
    return next(req);
  }
  const reply = dispatch(req);
  if (reply.status >= 400) {
    const error = new HttpErrorResponse({ status: reply.status, error: reply.body, url: req.url });
    // delay() does not delay errors, hence timer + mergeMap.
    return timer(latency()).pipe(mergeMap(() => throwError(() => error)));
  }
  return of(new HttpResponse({ status: reply.status, body: reply.body, url: req.url })).pipe(delay(latency()));
};

