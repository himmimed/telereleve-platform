// In-browser simulated data for the mock API (environment.useMockApi).
// Everything is generated from a fixed seed so the demo looks the same on every load, and the
// consumption time series are computed on the fly (no stored readings). All names are fictitious.
import {
  Alert,
  AlertStatus,
  ConsumptionStep,
  MapStatus,
  Meter,
  MeterDetails,
  MeterRequest,
  MeterStatus,
  MeterType,
  Reading,
  Role,
  RuleCode,
  RuleConfig,
  User,
  UserSummary,
} from '../models';
import { RULE_SEVERITY } from '../labels';
import { startOfDay, startOfMonth } from '../../shared/calendar';

export const MINUTE = 60_000;
export const HOUR = 60 * MINUTE;
export const DAY = 24 * HOUR;

/** Deterministic PRNG (mulberry32). */
export function seededRandom(seed: number): () => number {
  let state = seed >>> 0;
  return () => {
    state = (state + 0x6d2b79f5) >>> 0;
    let t = state;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Stable pseudo-random number in [0, 1) for a pair of integers (per-meter, per-hour noise). */
export function hash01(a: number, b: number): number {
  let h = Math.imul(a ^ 0x9e3779b9, 0x85ebca6b) ^ Math.imul(b | 0, 0xc2b2ae35);
  h ^= h >>> 16;
  h = Math.imul(h, 0x7feb352d);
  h ^= h >>> 15;
  h = Math.imul(h, 0x846ca68b);
  h ^= h >>> 16;
  return (h >>> 0) / 4294967296;
}

function normalise(profile: number[]): number[] {
  const mean = profile.reduce((sum, v) => sum + v, 0) / profile.length;
  return profile.map((v) => v / mean);
}

// Share of the daily consumption per hour: low at night, peaks in the morning and the evening.
const ELECTRICITY_PROFILE = normalise([
  0.42, 0.36, 0.33, 0.32, 0.33, 0.4, 0.62, 0.95, 1.08, 0.9, 0.8, 0.82, 0.92, 0.86, 0.78, 0.76, 0.84,
  1.02, 1.34, 1.58, 1.55, 1.3, 0.95, 0.62,
]);
const WATER_PROFILE = normalise([
  0.12, 0.08, 0.06, 0.06, 0.08, 0.25, 1.1, 1.75, 1.5, 0.95, 0.8, 0.85, 1.15, 1.05, 0.75, 0.65, 0.75,
  0.95, 1.3, 1.6, 1.45, 1.05, 0.6, 0.3,
]);

type Anomaly = 'ZERO' | 'SPIKE' | 'SILENT' | 'DECREASING' | 'VOLTAGE';

interface SimMeter {
  id: string;
  seq: number;
  serial: string;
  type: MeterType;
  clientName: string;
  address: string;
  latitude: number;
  longitude: number;
  readingIntervalMin: number;
  status: MeterStatus;
  createdAt: string;
  business: boolean;
  /** kWh or m³ per day on average. */
  dailyBase: number;
  /** Index value at INDEX_ANCHOR. */
  baseIndex: number;
  signal: number;
  anomaly?: Anomaly;
  /** For SILENT meters: time of the last reading received. */
  silentSince?: number;
  /** For DECREASING meters: when the index went down, and by how much. */
  decreaseAt?: number;
  decreaseBy?: number;
}

interface SimUser extends User {
  password: string;
}

const QUARTERS = [
  { name: 'Agdal', lat: 34.03, lng: -5.005 },
  { name: 'Ville Nouvelle', lat: 34.037, lng: -4.999 },
  { name: 'Narjiss', lat: 34.01, lng: -4.985 },
  { name: 'Saïss', lat: 34.0, lng: -5.02 },
  { name: 'Zouagha', lat: 34.042, lng: -5.045 },
  { name: 'Aïn Chkef', lat: 33.99, lng: -5.005 },
  { name: 'Route d’Imouzzer', lat: 34.018, lng: -5.01 },
  { name: 'Bensouda', lat: 34.045, lng: -5.065 },
  { name: 'Atlas', lat: 34.025, lng: -4.98 },
  { name: 'Fès Jdid', lat: 34.056, lng: -4.99 },
  { name: 'Montfleuri', lat: 34.022, lng: -4.97 },
  { name: 'Médina', lat: 34.064, lng: -4.973 },
];

const STREETS = [
  'Avenue Hassan II',
  'Rue Ibn Khaldoun',
  'Boulevard Allal El Fassi',
  'Rue Moulay Slimane',
  'Avenue des FAR',
  'Rue Ibn Sina',
  'Rue Abou Bakr Seddik',
  'Avenue Mohammed V',
  'Rue Oued Fès',
  'Rue des Orangers',
];

const HOUSEHOLDS = [
  'Foyer Alaoui', 'Foyer Benjelloun', 'Foyer Tazi', 'Foyer Berrada', 'Foyer El Idrissi',
  'Foyer Bennis', 'Foyer Chraibi', 'Foyer Lahlou', 'Foyer Sqalli', 'Foyer Kettani',
  'Foyer Lazrak', 'Foyer Bouzidi', 'Foyer Amrani', 'Foyer Ouazzani', 'Foyer Filali',
  'Foyer Hajji', 'Foyer Mernissi', 'Foyer Saadi', 'Foyer Zerhouni', 'Foyer Naciri',
  'Foyer Bekkali', 'Foyer Cherkaoui', 'Foyer Jabri', 'Foyer Rami', 'Foyer Guessous',
  'Foyer Benkirane', 'Foyer Mekouar', 'Foyer Slaoui', 'Foyer Tahiri', 'Foyer Ziani',
  'Foyer Belkhayat', 'Foyer Hassani', 'Foyer Iraqi', 'Foyer Kabbaj', 'Foyer Lamrani',
  'Foyer Moussaoui', 'Foyer Nejjar', 'Foyer Semlali',
];

// Not tied to a neighbourhood name, so a business never appears in the wrong part of town.
const BUSINESSES = [
  'Boulangerie Al Baraka', 'Pressing Ennour', 'Épicerie Assafa', 'Cabinet dentaire Ibn Sina',
  'Atelier de couture Assalam', 'Résidence Les Orangers', 'Menuiserie Al Amal', 'Hammam Ennakhil',
  'Salon de thé Les Jardins', 'Clinique vétérinaire Al Wafa', 'Supérette Al Khayr', 'Imprimerie Annour',
];

const BOOT = Date.now();
/**
 * Indexes are the meter's base index plus everything consumed since this fixed date, so an index
 * never goes backwards between two page loads (except for the simulated A4 anomalies).
 */
const INDEX_ANCHOR = Date.UTC(2025, 8, 1);

function uuid(prefix: string, n: number): string {
  const hex = n.toString(16).padStart(12, '0');
  return `${prefix}-0000-4000-8000-${hex}`;
}

function round(value: number, decimals = 2): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

function floorHour(t: number): number {
  return Math.floor(t / HOUR) * HOUR;
}

// ------------------------------------------------------------------------------------------------
// Seed data
// ------------------------------------------------------------------------------------------------

const ANOMALIES: Record<number, Anomaly> = {
  6: 'DECREASING',
  11: 'VOLTAGE',
  14: 'SILENT',
  20: 'SPIKE',
  25: 'ZERO',
  29: 'SILENT',
  33: 'SILENT',
  38: 'DECREASING',
  41: 'SILENT',
};
const INACTIVE = new Set([47, 49]);
const SILENT_MINUTES: Record<number, number> = { 14: 72, 29: 205, 33: 410, 41: 58 };

function buildMeters(): SimMeter[] {
  const random = seededRandom(20261011);
  let household = 0;
  let business = 0;
  const meters: SimMeter[] = [];

  for (let seq = 1; seq <= 50; seq++) {
    const i = seq - 1;
    const type: MeterType = [2, 5, 8].includes(i % 10) ? 'WATER' : 'ELECTRICITY';
    const isBusiness = i % 4 === 1;
    const quarter = QUARTERS[i % QUARTERS.length];
    const clientName = isBusiness
      ? BUSINESSES[business++ % BUSINESSES.length]
      : HOUSEHOLDS[household++ % HOUSEHOLDS.length];
    const dailyBase =
      type === 'ELECTRICITY'
        ? isBusiness ? 28 + random() * 55 : 5.5 + random() * 11
        : isBusiness ? 1.6 + random() * 2.8 : 0.28 + random() * 0.55;
    const anomaly = ANOMALIES[i];

    meters.push({
      id: uuid('a1b2c3d4', seq),
      seq,
      serial: `CEAC-${type === 'ELECTRICITY' ? 'E' : 'W'}-${String(seq).padStart(6, '0')}`,
      type,
      clientName,
      address: `${1 + Math.floor(random() * 180)}, ${STREETS[Math.floor(random() * STREETS.length)]}, ${quarter.name}, Fès`,
      latitude: round(quarter.lat + (random() - 0.5) * 0.014, 6),
      longitude: round(quarter.lng + (random() - 0.5) * 0.016, 6),
      readingIntervalMin: 15,
      status: INACTIVE.has(i) ? 'INACTIVE' : 'ACTIVE',
      createdAt: new Date(BOOT - (420 + Math.floor(random() * 300)) * DAY).toISOString(),
      business: isBusiness,
      dailyBase,
      baseIndex: round(type === 'ELECTRICITY' ? 2000 + random() * 9000 : 150 + random() * 600, 3),
      signal: -58 - Math.floor(random() * 32),
      anomaly,
      silentSince: anomaly === 'SILENT' ? BOOT - SILENT_MINUTES[i] * MINUTE : undefined,
      decreaseAt: anomaly === 'DECREASING' ? floorHour(BOOT - (i === 6 ? 2 : 27) * HOUR) : undefined,
      decreaseBy: anomaly === 'DECREASING' ? (type === 'ELECTRICITY' ? 45.5 : 3.8) : undefined,
    });
  }
  return meters;
}

const USERS: SimUser[] = [
  { id: uuid('u0000001', 1), username: 'admin', fullName: 'Youssef El Amrani', email: 'y.elamrani@telereleve.test', role: 'ADMIN', enabled: true, password: 'demo1234' },
  { id: uuid('u0000001', 2), username: 'operateur', fullName: 'Salma Bennani', email: 's.bennani@telereleve.test', role: 'OPERATOR', enabled: true, password: 'demo1234' },
  { id: uuid('u0000001', 3), username: 'technicien', fullName: 'Hamza Tazi', email: 'h.tazi@telereleve.test', role: 'TECHNICIAN', enabled: true, password: 'demo1234' },
  { id: uuid('u0000001', 4), username: 'technicien2', fullName: 'Nadia Alaoui', email: 'n.alaoui@telereleve.test', role: 'TECHNICIAN', enabled: true, password: 'demo1234' },
];

/** Demo accounts shown on the login page in mock mode. */
export const DEMO_ACCOUNTS: { username: string; password: string; role: Role }[] = USERS.map(
  ({ username, password, role }) => ({ username, password, role }),
);

const RULES: RuleConfig[] = [
  { ruleCode: 'A1', enabled: true, threshold: null, windowHours: 24 },
  { ruleCode: 'A2', enabled: true, threshold: 3, windowHours: 168 },
  { ruleCode: 'A3', enabled: true, threshold: 3, windowHours: null },
  { ruleCode: 'A4', enabled: true, threshold: null, windowHours: null },
  { ruleCode: 'A5', enabled: true, threshold: 10, windowHours: null },
];

const fr = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2, minimumFractionDigits: 1 });

function silentDuration(minutes: number): string {
  const h = Math.floor(minutes / 60);
  const m = minutes % 60;
  return h === 0 ? `${m} min` : `${h} h ${String(m).padStart(2, '0')}`;
}

// ------------------------------------------------------------------------------------------------
// Database
// ------------------------------------------------------------------------------------------------

export class MockDb {
  readonly meters: SimMeter[] = buildMeters();
  readonly users: SimUser[] = USERS.map((u) => ({ ...u }));
  readonly rules: RuleConfig[] = RULES.map((r) => ({ ...r }));
  readonly alerts: Alert[] = [];
  private alertSeq = 0;
  private userSeq = USERS.length;

  constructor() {
    this.seedAlerts();
  }

  // ---------------------------------------------------------------- consumption & readings

  lastReadingAt(m: SimMeter, now: number): number | null {
    if (m.status === 'INACTIVE') {
      return now - 9 * DAY - m.seq * HOUR;
    }
    if (m.silentSince !== undefined) {
      return m.silentSince;
    }
    // Each meter transmits at its own offset within the interval, as real fleets spread their
    // transmissions to avoid every meter hitting the network at the same second.
    const interval = m.readingIntervalMin * MINUTE;
    const offset = (m.seq * 53_000) % interval;
    return Math.floor((now - offset) / interval) * interval + offset;
  }

  /** Consumption of one meter for the full hour starting at `hourStart` (0 once it stops reporting). */
  hourly(m: SimMeter, hourStart: number, now: number): number {
    if (m.status === 'INACTIVE') {
      return 0;
    }
    if (m.anomaly === 'ZERO' && hourStart >= now - 30 * HOUR) {
      return 0;
    }
    // Platform local time (UTC+1): its UTC fields read as Moroccan time.
    const date = new Date(hourStart + HOUR);
    const profile = m.type === 'ELECTRICITY' ? ELECTRICITY_PROFILE : WATER_PROFILE;
    const weekend = date.getUTCDay() === 0 || date.getUTCDay() === 6;
    const weekFactor = weekend ? (m.business ? 0.55 : 1.12) : 1;
    const noise = 0.88 + hash01(m.seq, Math.floor(hourStart / HOUR)) * 0.24;
    let value = (m.dailyBase / 24) * profile[date.getUTCHours()] * weekFactor * noise;
    if (m.anomaly === 'SPIKE' && hourStart >= floorHour(now) - HOUR) {
      value *= 5;
    }
    return value;
  }

  /**
   * Consumption between two instants. Each hour contributes pro rata of its overlap with
   * [from, to), and nothing is consumed after the meter's last reading (or after `now`).
   */
  sum(m: SimMeter, from: number, to: number, now: number): number {
    const end = Math.min(to, now, this.lastReadingAt(m, now) ?? 0);
    let total = 0;
    for (let t = floorHour(from); t < end; t += HOUR) {
      const overlap = Math.min(t + HOUR, end) - Math.max(t, from);
      if (overlap > 0) {
        total += (this.hourly(m, t, now) * overlap) / HOUR;
      }
    }
    return total;
  }

  buckets(
    m: SimMeter | SimMeter[],
    from: number,
    to: number,
    step: ConsumptionStep,
    now: number,
  ): { bucket: number; value: number }[] {
    const meters = Array.isArray(m) ? m : [m];
    const result: { bucket: number; value: number }[] = [];
    let start = alignBucket(from, step);
    while (start < to) {
      const next = nextBucket(start, step);
      const lo = Math.max(start, from);
      const hi = Math.min(next, to, now);
      const value = hi > lo ? meters.reduce((sum, meter) => sum + this.sum(meter, lo, hi, now), 0) : 0;
      result.push({ bucket: start, value });
      start = next;
    }
    return result;
  }

  indexAt(m: SimMeter, t: number, now: number): number {
    let index = m.baseIndex + this.sum(m, INDEX_ANCHOR, t, now);
    if (m.decreaseAt !== undefined && t >= m.decreaseAt) {
      index -= m.decreaseBy ?? 0;
    }
    return round(index, 3);
  }

  voltageAt(m: SimMeter, t: number): number | null {
    if (m.type === 'WATER') {
      return null;
    }
    if (m.anomaly === 'VOLTAGE' && t >= BOOT - 6 * HOUR) {
      return round(257.2 + hash01(m.seq, t / MINUTE) * 3, 1);
    }
    return round(230 * (0.98 + hash01(m.seq + 1000, t / MINUTE) * 0.04), 1);
  }

  lastReading(m: SimMeter, now: number): Reading | null {
    const at = this.lastReadingAt(m, now);
    if (at === null) {
      return null;
    }
    return {
      timestamp: new Date(at).toISOString(),
      index: this.indexAt(m, at, now),
      voltage: this.voltageAt(m, at),
      signalStrength: m.signal,
    };
  }

  /** Readings every interval in [from, to), for the CSV export. */
  readings(m: SimMeter, from: number, to: number, now: number): Reading[] {
    const interval = m.readingIntervalMin * MINUTE;
    const last = this.lastReadingAt(m, now) ?? 0;
    const end = Math.min(to, last + 1);
    const readings: Reading[] = [];
    let t = Math.ceil(from / interval) * interval;
    let index = t < end ? this.indexAt(m, t, now) : 0;
    while (t < end) {
      readings.push({
        timestamp: new Date(t).toISOString(),
        index: round(index, 3),
        voltage: this.voltageAt(m, t),
        signalStrength: m.signal - Math.round(hash01(m.seq, t / interval) * 4),
      });
      const next = t + interval;
      index += this.sum(m, t, next, now);
      if (m.decreaseAt !== undefined && t < m.decreaseAt && next >= m.decreaseAt) {
        index -= m.decreaseBy ?? 0;
      }
      t = next;
    }
    return readings;
  }

  // ---------------------------------------------------------------- meters

  toMeter(m: SimMeter, now: number): Meter {
    const last = this.lastReadingAt(m, now);
    return {
      id: m.id,
      serial: m.serial,
      type: m.type,
      clientName: m.clientName,
      address: m.address,
      latitude: m.latitude,
      longitude: m.longitude,
      readingIntervalMin: m.readingIntervalMin,
      status: m.status,
      lastReadingAt: last === null ? null : new Date(last).toISOString(),
      createdAt: m.createdAt,
    };
  }

  details(m: SimMeter, now: number): MeterDetails {
    return {
      ...this.toMeter(m, now),
      lastReading: this.lastReading(m, now),
      openAlerts: this.alerts.filter((a) => a.meterId === m.id && a.status !== 'RESOLVED'),
    };
  }

  isSilent(m: SimMeter, now: number): boolean {
    const last = this.lastReadingAt(m, now);
    return m.status === 'ACTIVE' && (last === null || now - last > 3 * m.readingIntervalMin * MINUTE);
  }

  mapStatus(m: SimMeter, now: number): MapStatus {
    if (this.isSilent(m, now)) {
      return 'SILENT';
    }
    return this.alerts.some((a) => a.meterId === m.id && a.status !== 'RESOLVED') ? 'ALERT' : 'OK';
  }

  findMeter(id: string): SimMeter | undefined {
    return this.meters.find((m) => m.id === id);
  }

  createMeter(request: MeterRequest): SimMeter {
    const seq = this.meters.length + 1;
    const meter: SimMeter = {
      ...request,
      id: uuid('a1b2c3d4', seq),
      seq,
      createdAt: new Date().toISOString(),
      business: false,
      dailyBase: request.type === 'ELECTRICITY' ? 9 : 0.45,
      baseIndex: 0,
      signal: -70,
    };
    this.meters.push(meter);
    return meter;
  }

  updateMeter(meter: SimMeter, request: MeterRequest): void {
    Object.assign(meter, request);
  }

  // ---------------------------------------------------------------- users

  summary(user: User | undefined): UserSummary | null {
    return user ? { id: user.id, fullName: user.fullName } : null;
  }

  createUser(request: Omit<SimUser, 'id'>): SimUser {
    const user: SimUser = { ...request, id: uuid('u0000001', ++this.userSeq) };
    this.users.push(user);
    return user;
  }

  // ---------------------------------------------------------------- alerts

  private addAlert(
    meter: SimMeter,
    ruleCode: RuleCode,
    status: AlertStatus,
    detectedAt: number,
    message: string,
    assignee?: string,
    resolvedAt?: number,
  ): void {
    this.alerts.push({
      id: uuid('ae000000', ++this.alertSeq),
      meterId: meter.id,
      meterSerial: meter.serial,
      meterType: meter.type,
      ruleCode,
      severity: RULE_SEVERITY[ruleCode],
      status,
      message,
      detectedAt: new Date(detectedAt).toISOString(),
      resolvedAt: resolvedAt ? new Date(resolvedAt).toISOString() : null,
      assignedTo: this.summary(this.users.find((u) => u.username === assignee)),
    });
  }

  private seedAlerts(): void {
    const now = BOOT;
    const m = (seq: number) => this.meters[seq - 1];

    // Open alerts, consistent with the injected anomalies above.
    for (const meter of this.meters.filter((x) => x.anomaly === 'DECREASING')) {
      const before = this.indexAt(meter, (meter.decreaseAt ?? now) - MINUTE, now);
      const after = round(before - (meter.decreaseBy ?? 0), 3);
      const unit = meter.type === 'ELECTRICITY' ? 'kWh' : 'm³';
      this.addAlert(
        meter,
        'A4',
        meter.seq === 7 ? 'NEW' : 'IN_PROGRESS',
        (meter.decreaseAt ?? now) + 2 * MINUTE,
        `Index en baisse : ${fr.format(before)} ${unit} puis ${fr.format(after)} ${unit}. Fraude possible ou remplacement du compteur.`,
        meter.seq === 7 ? undefined : 'technicien',
      );
    }
    for (const meter of this.meters.filter((x) => x.anomaly === 'SILENT')) {
      const minutes = Math.round((now - (meter.silentSince ?? now)) / MINUTE);
      this.addAlert(
        meter,
        'A3',
        meter.seq === 34 ? 'IN_PROGRESS' : 'NEW',
        (meter.silentSince ?? now) + 45 * MINUTE,
        `Aucune relève depuis ${silentDuration(minutes)} (intervalle attendu : ${meter.readingIntervalMin} min).`,
        meter.seq === 34 ? 'technicien2' : undefined,
      );
    }
    this.addAlert(m(12), 'A5', 'IN_PROGRESS', now - 5 * HOUR - 12 * MINUTE,
      'Tension mesurée à 258,4 V, hors de la plage autorisée (207 à 253 V).', 'technicien');
    this.addAlert(m(21), 'A2', 'NEW', now - 38 * MINUTE,
      'Consommation de la dernière heure 4,6 fois supérieure à la moyenne de cette heure sur 7 jours.');
    this.addAlert(m(26), 'A1', 'IN_PROGRESS', now - 6 * HOUR,
      'Aucune consommation enregistrée depuis 24 h sur un compteur actif.', 'technicien2');

    // History: resolved alerts over the last 30 days.
    const random = seededRandom(7);
    const history: { rule: RuleCode; message: string; assignee?: string }[] = [
      { rule: 'A3', message: 'Aucune relève depuis 1 h 05 (intervalle attendu : 15 min).' },
      { rule: 'A3', message: 'Aucune relève depuis 2 h 40 (intervalle attendu : 15 min).', assignee: 'technicien' },
      { rule: 'A5', message: 'Tension mesurée à 204,1 V, hors de la plage autorisée (207 à 253 V).', assignee: 'technicien2' },
      { rule: 'A2', message: 'Consommation de la dernière heure 3,4 fois supérieure à la moyenne de cette heure sur 7 jours.' },
      { rule: 'A1', message: 'Aucune consommation enregistrée depuis 24 h sur un compteur actif.', assignee: 'technicien' },
      { rule: 'A3', message: 'Aucune relève depuis 55 min (intervalle attendu : 15 min).' },
      { rule: 'A4', message: 'Index en baisse après remplacement du compteur, confirmé sur place.', assignee: 'technicien' },
      { rule: 'A5', message: 'Tension mesurée à 255,7 V, hors de la plage autorisée (207 à 253 V).' },
      { rule: 'A3', message: 'Aucune relève depuis 3 h 15 (intervalle attendu : 15 min).', assignee: 'technicien2' },
      { rule: 'A2', message: 'Consommation de la dernière heure 5,2 fois supérieure à la moyenne de cette heure sur 7 jours.' },
      { rule: 'A3', message: 'Aucune relève depuis 1 h 30 (intervalle attendu : 15 min).' },
      { rule: 'A1', message: 'Aucune consommation enregistrée depuis 24 h sur un compteur actif.' },
      { rule: 'A5', message: 'Tension mesurée à 205,6 V, hors de la plage autorisée (207 à 253 V).', assignee: 'technicien' },
      { rule: 'A3', message: 'Aucune relève depuis 4 h 10 (intervalle attendu : 15 min).' },
      { rule: 'A2', message: 'Consommation de la dernière heure 3,9 fois supérieure à la moyenne de cette heure sur 7 jours.', assignee: 'technicien2' },
      { rule: 'A3', message: 'Aucune relève depuis 1 h 50 (intervalle attendu : 15 min).' },
    ];
    history.forEach((entry, i) => {
      const electricOnly = entry.rule === 'A5';
      const candidates = this.meters.filter(
        (x) => x.status === 'ACTIVE' && (!electricOnly || x.type === 'ELECTRICITY'),
      );
      const meter = i < 4 ? m([7, 12, 21, 15][i]) : candidates[Math.floor(random() * candidates.length)];
      const detectedAt = now - (2 + i * 1.8 + random()) * DAY;
      const resolvedAt = detectedAt + (0.6 + random() * 20) * HOUR;
      this.addAlert(meter, entry.rule, 'RESOLVED', detectedAt, entry.message, entry.assignee, resolvedAt);
    });
  }
}

/** Buckets on the platform calendar (UTC+1), like the backend. */
export function alignBucket(t: number, step: ConsumptionStep): number {
  if (step === 'hour') {
    return floorHour(t);
  }
  return (step === 'day' ? startOfDay(new Date(t)) : startOfMonth(new Date(t))).getTime();
}

export function nextBucket(t: number, step: ConsumptionStep): number {
  if (step === 'hour') {
    return t + HOUR;
  }
  if (step === 'day') {
    return t + 24 * HOUR; // fixed offset: every day lasts 24 h
  }
  return startOfMonth(new Date(t), -1).getTime();
}
