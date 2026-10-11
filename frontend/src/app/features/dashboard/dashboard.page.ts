import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import {
  Subject,
  catchError,
  combineLatest,
  filter,
  forkJoin,
  map,
  merge,
  of,
  switchMap,
  timer,
} from 'rxjs';
import { AlertApi, DashboardApi, MeterApi } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { RULE_LABEL } from '../../core/labels';
import { Alert, DashboardStats, MapMarker, MapStatus, MeterType, Severity, TrendPoint } from '../../core/models';
import { ChartPoint, ConsumptionChart } from '../../shared/consumption-chart';
import { NumberPipe, RelativeTimePipe, bucketLabels } from '../../shared/format';
import { EmptyState, MeterTypeLabel, PageHeader, SeverityBadge, Skeleton } from '../../shared/ui';

type TrendPeriod = '24h' | '7d';

interface DashboardData {
  stats: DashboardStats;
  trend: TrendPoint[];
  alerts: Alert[];
  markers: MapMarker[];
  loadedAt: Date;
}

const REFRESH_MS = 30_000;
const SEVERITY_ORDER: Record<Severity, number> = { CRITICAL: 0, HIGH: 1, MEDIUM: 2 };

@Component({
  selector: 'app-dashboard-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatTooltipModule,
    PageHeader,
    Skeleton,
    EmptyState,
    SeverityBadge,
    MeterTypeLabel,
    ConsumptionChart,
    NumberPipe,
    RelativeTimePipe,
  ],
  templateUrl: './dashboard.page.html',
  styleUrl: './dashboard.page.scss',
})
export class DashboardPage {
  private readonly dashboardApi = inject(DashboardApi);
  private readonly alertApi = inject(AlertApi);
  private readonly meterApi = inject(MeterApi);
  private readonly document = inject(DOCUMENT);
  protected readonly auth = inject(AuthStore);
  protected readonly ruleLabel = RULE_LABEL;

  protected readonly period = signal<TrendPeriod>('24h');
  protected readonly energy = signal<MeterType>('ELECTRICITY');
  protected readonly failed = signal(false);
  private readonly manualRefresh$ = new Subject<void>();

  /** Every 30 s while the tab is visible, on demand, and whenever the trend period changes. */
  protected readonly data = toSignal(
    combineLatest([
      toObservable(this.period),
      merge(
        timer(0, REFRESH_MS).pipe(filter(() => !this.document.hidden)),
        this.manualRefresh$,
      ),
    ]).pipe(
      switchMap(([period]) => this.load(period)),
      filter((data): data is DashboardData => data !== null),
    ),
  );

  protected readonly updatedLabel = computed(() => {
    const loadedAt = this.data()?.loadedAt;
    return loadedAt
      ? `Actualisé à ${loadedAt.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}, mise à jour automatique toutes les 30 s`
      : 'Chargement des indicateurs';
  });

  protected readonly openAlertTotal = computed(() => {
    const open = this.data()?.stats.openAlerts;
    return open ? open.CRITICAL + open.HIGH + open.MEDIUM : 0;
  });

  protected readonly chartPoints = computed<ChartPoint[]>(() => {
    const trend = this.data()?.trend ?? [];
    const step = this.period() === '24h' ? 'hour' : 'day';
    const electricity = this.energy() === 'ELECTRICITY';
    return trend.map((p) => ({
      ...bucketLabels(p.bucket, step),
      value: electricity ? p.electricityKwh : p.waterM3,
    }));
  });

  protected readonly chartUnit = computed(() => (this.energy() === 'ELECTRICITY' ? 'kWh' : 'm³'));
  protected readonly chartTotal = computed(() => this.chartPoints().reduce((sum, p) => sum + p.value, 0));

  protected readonly fleet = computed(() => {
    const markers = this.data()?.markers ?? [];
    const count = (status: MapStatus) => markers.filter((m) => m.status === status).length;
    const total = markers.length || 1;
    return (['OK', 'ALERT', 'SILENT'] as MapStatus[]).map((status) => ({
      status,
      count: count(status),
      share: (count(status) / total) * 100,
    }));
  });

  protected readonly fleetLabels: Record<MapStatus, string> = {
    OK: 'Normal',
    ALERT: 'En alerte',
    SILENT: 'Silencieux',
  };

  protected refresh(): void {
    this.manualRefresh$.next();
  }

  private load(period: TrendPeriod) {
    const now = new Date();
    const from = new Date(now.getTime() - (period === '24h' ? 24 : 7 * 24) * 3_600_000);
    if (period === '7d') {
      from.setHours(0, 0, 0, 0);
    }
    const range = { from: from.toISOString(), to: now.toISOString() };
    const openAlerts = (status: 'NEW' | 'IN_PROGRESS') =>
      this.alertApi.search({ status, page: 0, size: 6 }).pipe(map((page) => page.content));

    return forkJoin({
      stats: this.dashboardApi.stats(),
      trend: this.dashboardApi.consumptionTrend(range, period === '24h' ? 'hour' : 'day'),
      fresh: openAlerts('NEW'),
      ongoing: openAlerts('IN_PROGRESS'),
      markers: this.meterApi.map(),
    }).pipe(
      map(({ stats, trend, fresh, ongoing, markers }): DashboardData => {
        this.failed.set(false);
        const alerts = [...fresh, ...ongoing]
          .sort(
            (a, b) =>
              SEVERITY_ORDER[a.severity] - SEVERITY_ORDER[b.severity] ||
              Date.parse(b.detectedAt) - Date.parse(a.detectedAt),
          )
          .slice(0, 6);
        return { stats, trend, alerts, markers, loadedAt: new Date() };
      }),
      catchError(() => {
        this.failed.set(true);
        return of(null);
      }),
    );
  }
}
