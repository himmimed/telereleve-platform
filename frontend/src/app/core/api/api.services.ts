// Typed clients of the REST API. Components never call HttpClient directly.
import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  Alert,
  AlertQuery,
  AlertUpdate,
  Consumption,
  ConsumptionStep,
  DashboardStats,
  MapMarker,
  Meter,
  MeterDetails,
  MeterQuery,
  MeterRequest,
  Page,
  RuleCode,
  RuleConfig,
  TrendPoint,
  User,
  UserRequest,
  UserSummary,
} from '../models';

const API = environment.apiBaseUrl;

/** Builds query params, skipping null / undefined / empty values. */
export function toParams(query: object): HttpParams {
  let params = new HttpParams();
  for (const [key, value] of Object.entries(query)) {
    if (value !== null && value !== undefined && value !== '') {
      params = params.set(key, String(value));
    }
  }
  return params;
}

export interface Period {
  from: string;
  to: string;
}

@Injectable({ providedIn: 'root' })
export class MeterApi {
  private readonly http = inject(HttpClient);

  search(query: MeterQuery): Observable<Page<Meter>> {
    return this.http.get<Page<Meter>>(`${API}/meters`, { params: toParams(query) });
  }

  get(id: string): Observable<MeterDetails> {
    return this.http.get<MeterDetails>(`${API}/meters/${id}`);
  }

  create(request: MeterRequest): Observable<Meter> {
    return this.http.post<Meter>(`${API}/meters`, request);
  }

  update(id: string, request: MeterRequest): Observable<Meter> {
    return this.http.put<Meter>(`${API}/meters/${id}`, request);
  }

  consumption(id: string, period: Period, step: ConsumptionStep): Observable<Consumption> {
    return this.http.get<Consumption>(`${API}/meters/${id}/consumption`, {
      params: toParams({ ...period, step }),
    });
  }

  exportCsv(id: string, period: Period): Observable<Blob> {
    return this.http.get(`${API}/meters/${id}/export.csv`, {
      params: toParams(period),
      responseType: 'blob',
    });
  }

  map(): Observable<MapMarker[]> {
    return this.http.get<MapMarker[]>(`${API}/meters/map`);
  }
}

@Injectable({ providedIn: 'root' })
export class AlertApi {
  private readonly http = inject(HttpClient);

  search(query: AlertQuery): Observable<Page<Alert>> {
    return this.http.get<Page<Alert>>(`${API}/alerts`, { params: toParams(query) });
  }

  update(id: string, update: AlertUpdate): Observable<Alert> {
    return this.http.patch<Alert>(`${API}/alerts/${id}`, update);
  }
}

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private readonly http = inject(HttpClient);

  stats(): Observable<DashboardStats> {
    return this.http.get<DashboardStats>(`${API}/dashboard/stats`);
  }

  /** Extension of the specification (docs/decisions.md, D12). */
  consumptionTrend(period: Period, step: ConsumptionStep): Observable<TrendPoint[]> {
    return this.http.get<TrendPoint[]>(`${API}/dashboard/consumption-trend`, {
      params: toParams({ ...period, step }),
    });
  }
}

@Injectable({ providedIn: 'root' })
export class RuleApi {
  private readonly http = inject(HttpClient);

  list(): Observable<RuleConfig[]> {
    return this.http.get<RuleConfig[]>(`${API}/rules`);
  }

  update(code: RuleCode, config: RuleConfig): Observable<RuleConfig> {
    return this.http.put<RuleConfig>(`${API}/rules/${code}`, config);
  }
}

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  list(): Observable<User[]> {
    return this.http.get<User[]>(`${API}/users`);
  }

  /** Technicians an alert can be assigned to (extension, docs/decisions.md, D12). */
  technicians(): Observable<UserSummary[]> {
    return this.http.get<UserSummary[]>(`${API}/users/technicians`);
  }

  create(request: UserRequest): Observable<User> {
    return this.http.post<User>(`${API}/users`, request);
  }

  update(id: string, request: UserRequest): Observable<User> {
    return this.http.put<User>(`${API}/users/${id}`, request);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API}/users/${id}`);
  }
}
