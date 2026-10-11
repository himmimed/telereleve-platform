import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  computed,
  effect,
  inject,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import type * as Leaflet from 'leaflet';
import { catchError, combineLatest, map, of, startWith, switchMap } from 'rxjs';
import { AlertApi, MeterApi, Period } from '../../core/api/api.services';
import { AuthStore } from '../../core/auth/auth.store';
import { METER_STATUS_LABEL, RULE_LABEL, UNIT_LABEL } from '../../core/labels';
import { isVoltageInRange, meterHealth, signalQuality } from '../../core/meter-rules';
import { Alert, Consumption, ConsumptionStep, Meter, MeterDetails } from '../../core/models';
import { ThemeService, resolveColor } from '../../core/theme.service';
import { ChartPoint, ConsumptionChart } from '../../shared/consumption-chart';
import { DateTimePipe, NumberPipe, RelativeTimePipe, bucketLabels } from '../../shared/format';
import { baseLayer } from '../../shared/map-tiles';
import {
  AlertStatusBadge,
  Badge,
  EmptyState,
  MapStatusBadge,
  MeterTypeLabel,
  SeverityBadge,
  Skeleton,
} from '../../shared/ui';
import { MeterFormDialog } from './meter-form.dialog';

type PeriodKey = '24h' | '7d' | '30d' | '12m';

const PERIODS: Record<PeriodKey, { label: string; step: ConsumptionStep; caption: string }> = {
  '24h': { label: '24 h', step: 'hour', caption: 'les dernières 24 h, par heure' },
  '7d': { label: '7 jours', step: 'day', caption: 'les 7 derniers jours, par jour' },
  '30d': { label: '30 jours', step: 'day', caption: 'les 30 derniers jours, par jour' },
  '12m': { label: '12 mois', step: 'month', caption: 'les 12 derniers mois, par mois' },
};

/** Start of the period, aligned on whole buckets (midnight / first day of month). */
export function periodRange(key: PeriodKey, now = new Date()): Period {
  const from = new Date(now);
  switch (key) {
    case '24h':
      from.setHours(from.getHours() - 23, 0, 0, 0);
      break;
    case '7d':
      from.setDate(from.getDate() - 6);
      from.setHours(0, 0, 0, 0);
      break;
    case '30d':
      from.setDate(from.getDate() - 29);
      from.setHours(0, 0, 0, 0);
      break;
    case '12m':
      from.setMonth(from.getMonth() - 11, 1);
      from.setHours(0, 0, 0, 0);
      break;
  }
  return { from: from.toISOString(), to: now.toISOString() };
}

type Loaded<T> = { status: 'loading' } | { status: 'error' } | { status: 'ready'; value: T };

@Component({
  selector: 'app-meter-detail-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    MatButtonModule,
    MatButtonToggleModule,
    MatIconModule,
    MatTableModule,
    MatTooltipModule,
    Skeleton,
    EmptyState,
    Badge,
    MapStatusBadge,
    MeterTypeLabel,
    SeverityBadge,
    AlertStatusBadge,
    ConsumptionChart,
    NumberPipe,
    RelativeTimePipe,
    DateTimePipe,
  ],
  templateUrl: './meter-detail.page.html',
  styleUrl: './meter-detail.page.scss',
})
export class MeterDetailPage {
  /** Route parameter (component input binding). */
  readonly id = input.required<string>();

  private readonly meterApi = inject(MeterApi);
  private readonly alertApi = inject(AlertApi);
  private readonly dialog = inject(MatDialog);
  private readonly snackBar = inject(MatSnackBar);
  private readonly theme = inject(ThemeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  protected readonly auth = inject(AuthStore);

  protected readonly periods = Object.entries(PERIODS).map(([key, p]) => ({ key: key as PeriodKey, label: p.label }));
  protected readonly ruleLabel = RULE_LABEL;
  protected readonly statusLabel = METER_STATUS_LABEL;
  protected readonly alertColumns = ['severity', 'rule', 'message', 'detectedAt', 'status', 'resolvedAt'];

  protected readonly period = signal<PeriodKey>('7d');
  protected readonly exporting = signal(false);
  private readonly version = signal(0);

  protected readonly meter = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.version)]).pipe(
      switchMap(([id]) =>
        this.meterApi.get(id).pipe(
          map((value): Loaded<MeterDetails> => ({ status: 'ready', value })),
          catchError(() => of<Loaded<MeterDetails>>({ status: 'error' })),
          startWith<Loaded<MeterDetails>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as Loaded<MeterDetails> },
  );

  protected readonly details = computed(() => {
    const state = this.meter();
    return state.status === 'ready' ? state.value : null;
  });

  protected readonly consumption = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.period)]).pipe(
      switchMap(([id, period]) =>
        this.meterApi.consumption(id, periodRange(period), PERIODS[period].step).pipe(
          map((value): Loaded<Consumption> => ({ status: 'ready', value })),
          catchError(() => of<Loaded<Consumption>>({ status: 'error' })),
          startWith<Loaded<Consumption>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as Loaded<Consumption> },
  );

  protected readonly alerts = toSignal(
    combineLatest([toObservable(this.id), toObservable(this.version)]).pipe(
      switchMap(([meterId]) =>
        this.alertApi.search({ meterId, page: 0, size: 20 }).pipe(
          map((page): Loaded<Alert[]> => ({ status: 'ready', value: page.content })),
          catchError(() => of<Loaded<Alert[]>>({ status: 'error' })),
          startWith<Loaded<Alert[]>>({ status: 'loading' }),
        ),
      ),
    ),
    { initialValue: { status: 'loading' } as Loaded<Alert[]> },
  );

  protected readonly alertRows = computed(() => {
    const state = this.alerts();
    return state.status === 'ready' ? state.value : null;
  });

  protected readonly health = computed(() => {
    const meter = this.details();
    return meter ? meterHealth(meter, meter.openAlerts.length) : null;
  });

  protected readonly unit = computed(() => (this.details()?.type === 'WATER' ? UNIT_LABEL.m3 : UNIT_LABEL.kWh));

  protected readonly chartPoints = computed<ChartPoint[]>(() => {
    const state = this.consumption();
    if (state.status !== 'ready') {
      return [];
    }
    return state.value.points.map((p) => ({ ...bucketLabels(p.bucket, state.value.step), value: p.value }));
  });

  protected readonly periodCaption = computed(() => PERIODS[this.period()].caption);

  protected readonly voltageOk = computed(() => {
    const voltage = this.details()?.lastReading?.voltage;
    return voltage === null || voltage === undefined ? null : isVoltageInRange(voltage);
  });

  protected readonly radio = computed(() => {
    const dbm = this.details()?.lastReading?.signalStrength;
    return dbm === undefined ? null : { dbm, quality: signalQuality(dbm) };
  });

  // Location map
  private readonly mapElement = viewChild<ElementRef<HTMLElement>>('map');
  private miniMap?: { L: typeof Leaflet; map: Leaflet.Map; tiles: Leaflet.TileLayer; marker: Leaflet.CircleMarker };

  constructor() {
    effect(() => {
      const element = this.mapElement()?.nativeElement;
      const meter = this.details();
      const dark = this.theme.isDark();
      const health = this.health();
      if (element && meter && health) {
        void this.renderMap(element, meter, dark, health);
      }
    });
    inject(DestroyRef).onDestroy(() => this.miniMap?.map.remove());
  }

  private async renderMap(element: HTMLElement, meter: Meter, dark: boolean, health: string): Promise<void> {
    const L = await import('leaflet');
    const color = resolveColor(
      health === 'OK' ? 'var(--status-ok)' : health === 'ALERT' ? 'var(--status-alert)' : 'var(--status-silent)',
      this.host,
    );
    const ring = resolveColor('var(--app-panel)', this.host);
    const position: [number, number] = [meter.latitude, meter.longitude];
    const style = { color: ring, weight: 2, fillColor: color, fillOpacity: 1 };
    if (!this.miniMap) {
      // The view must be set before any layer is added, otherwise Leaflet cannot project it.
      const map = L.map(element, {
        center: position,
        zoom: 15,
        zoomControl: false,
        scrollWheelZoom: false,
      });
      // Leaflet measures its container once; re-measure when the grid layout settles or resizes.
      const resize = new ResizeObserver(() => map.invalidateSize());
      resize.observe(element);
      map.on('unload', () => resize.disconnect());
      this.miniMap = {
        L,
        map,
        tiles: baseLayer(L, dark).addTo(map),
        marker: L.circleMarker(position, { radius: 9, ...style }).addTo(map),
      };
      return;
    }
    this.miniMap.tiles.remove();
    this.miniMap.tiles = baseLayer(L, dark).addTo(this.miniMap.map);
    this.miniMap.marker.setLatLng(position).setStyle(style);
    this.miniMap.map.setView(position, 15);
  }

  protected ruleName(alert: Alert): string {
    return RULE_LABEL[alert.ruleCode];
  }

  protected edit(): void {
    const meter = this.details();
    if (!meter) {
      return;
    }
    this.dialog
      .open(MeterFormDialog, { data: meter, width: '620px' })
      .afterClosed()
      .subscribe((updated?: Meter) => {
        if (updated) {
          this.snackBar.open('Compteur mis à jour.', 'OK');
          this.version.update((v) => v + 1);
        }
      });
  }

  protected exportCsv(): void {
    const meter = this.details();
    if (!meter || this.exporting()) {
      return;
    }
    this.exporting.set(true);
    const range = periodRange(this.period());
    this.meterApi.exportCsv(meter.id, range).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${meter.serial}_${range.from.slice(0, 10)}_${range.to.slice(0, 10)}.csv`;
        link.click();
        URL.revokeObjectURL(url);
        this.exporting.set(false);
      },
      error: () => this.exporting.set(false),
    });
  }
}
