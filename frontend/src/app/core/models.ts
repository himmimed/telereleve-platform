// DTOs of the REST API (see the specification, section "REST API").
// Kept in one file: they are the contract between the Angular app and the Spring Boot backend.

export type MeterType = 'ELECTRICITY' | 'WATER';
export type MeterStatus = 'ACTIVE' | 'INACTIVE';
export type MapStatus = 'OK' | 'ALERT' | 'SILENT';
export type Role = 'ADMIN' | 'OPERATOR' | 'TECHNICIAN';
export type RuleCode = 'A1' | 'A2' | 'A3' | 'A4' | 'A5';
export type Severity = 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertStatus = 'NEW' | 'IN_PROGRESS' | 'RESOLVED';
export type ConsumptionStep = 'hour' | 'day' | 'month';
export type ConsumptionUnit = 'kWh' | 'm3';

/** Spring Data page, as serialised by the backend. `page` is zero-based. */
export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/** RFC 7807 error body returned by the backend's @RestControllerAdvice. */
export interface ProblemDetail {
  type?: string;
  title: string;
  status: number;
  detail?: string;
  errors?: Record<string, string>;
}

export interface Meter {
  id: string;
  serial: string;
  type: MeterType;
  clientName: string;
  address: string;
  latitude: number;
  longitude: number;
  readingIntervalMin: number;
  status: MeterStatus;
  lastReadingAt: string | null;
  createdAt: string;
}

export interface MeterRequest {
  serial: string;
  type: MeterType;
  clientName: string;
  address: string;
  latitude: number;
  longitude: number;
  readingIntervalMin: number;
  status: MeterStatus;
}

export interface Reading {
  timestamp: string;
  index: number;
  voltage: number | null;
  signalStrength: number;
}

export interface MeterDetails extends Meter {
  lastReading: Reading | null;
  openAlerts: Alert[];
}

export interface MeterQuery {
  type?: MeterType | null;
  status?: MeterStatus | null;
  search?: string | null;
  page: number;
  size: number;
}

export interface ConsumptionPoint {
  /** Start of the bucket (ISO-8601). */
  bucket: string;
  value: number;
}

export interface Consumption {
  meterId: string;
  unit: ConsumptionUnit;
  step: ConsumptionStep;
  total: number;
  points: ConsumptionPoint[];
}

/**
 * Map marker. The specification lists id, serial, lat, lng and status; type and clientName are
 * added so the popup can describe the meter without a second request (docs/decisions.md, D12).
 */
export interface MapMarker {
  id: string;
  serial: string;
  type: MeterType;
  clientName: string;
  lat: number;
  lng: number;
  status: MapStatus;
}

export interface UserSummary {
  id: string;
  fullName: string;
}

export interface Alert {
  id: string;
  meterId: string;
  meterSerial: string;
  meterType: MeterType;
  ruleCode: RuleCode;
  severity: Severity;
  status: AlertStatus;
  message: string;
  detectedAt: string;
  resolvedAt: string | null;
  assignedTo: UserSummary | null;
}

export interface AlertQuery {
  status?: AlertStatus | null;
  severity?: Severity | null;
  ruleCode?: RuleCode | null;
  meterId?: string | null;
  page: number;
  size: number;
}

export interface AlertUpdate {
  status?: AlertStatus;
  /** User id, or null to unassign. */
  assignedTo?: string | null;
}

export interface DashboardStats {
  activeMeters: number;
  silentMeters: number;
  openAlerts: Record<Severity, number>;
  /** Split by energy: kWh and m³ cannot be summed (docs/decisions.md, D12). */
  consumptionToday: {
    electricityKwh: number;
    waterM3: number;
  };
}

export interface TrendPoint {
  bucket: string;
  electricityKwh: number;
  waterM3: number;
}

export interface RuleConfig {
  ruleCode: RuleCode;
  enabled: boolean;
  threshold: number | null;
  windowHours: number | null;
}

export interface User {
  id: string;
  username: string;
  fullName: string;
  email: string;
  role: Role;
  enabled: boolean;
}

export interface UserRequest {
  username: string;
  fullName: string;
  email: string;
  role: Role;
  enabled: boolean;
  /** Required on creation, optional on update (blank keeps the current password). */
  password?: string | null;
}

export interface LoginResponse {
  token: string;
  role: Role;
  fullName: string;
}
