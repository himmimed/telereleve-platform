import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
import {
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { ThemeService, resolveColor } from '../core/theme.service';
import { formatNumber } from './format';

function prefersReducedMotion(): boolean {
  return typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches;
}

export interface ChartPoint {
  /** Short axis label ("14 h", "lun. 6"). */
  label: string;
  /** Full label for the tooltip and the accessible table ("lundi 6 octobre, 14 h"). */
  fullLabel: string;
  value: number;
}

/**
 * Single-series consumption chart (bars or line). One series, one axis: no legend, the panel title
 * names the series. Colours are read from the theme tokens and follow light / dark mode.
 */
@Component({
  selector: 'app-consumption-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [BaseChartDirective],
  providers: [
    provideCharts({
      registerables: [
        BarController,
        BarElement,
        LineController,
        LineElement,
        PointElement,
        LinearScale,
        CategoryScale,
        Tooltip,
        Filler,
      ],
    }),
  ],
  template: `
    <div class="canvas-wrap">
      <canvas baseChart [type]="kind()" [data]="config().data" [options]="config().options" role="img" [attr.aria-label]="ariaLabel()"></canvas>
    </div>
    <table class="visually-hidden">
      <caption>{{ ariaLabel() }}</caption>
      <thead><tr><th scope="col">Période</th><th scope="col">Consommation ({{ unit() }})</th></tr></thead>
      <tbody>
        @for (point of points(); track point.fullLabel) {
          <tr><td>{{ point.fullLabel }}</td><td>{{ format(point.value) }}</td></tr>
        }
      </tbody>
    </table>
  `,
  styles: `
    :host { display: block; position: relative; }
    .canvas-wrap { position: relative; height: var(--chart-height, 280px); }
  `,
})
export class ConsumptionChart {
  readonly points = input.required<ChartPoint[]>();
  readonly unit = input.required<string>();
  readonly kind = input<'bar' | 'line'>('bar');
  readonly ariaLabel = input('Graphique de consommation');
  readonly decimals = input(1);

  private readonly theme = inject(ThemeService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  protected readonly config = computed(() => {
    // Depend on the theme so colours are re-resolved when it changes.
    this.theme.isDark();
    const points = this.points();
    const unit = this.unit();
    const decimals = this.decimals();
    const kind = this.kind();

    const accent = resolveColor('var(--mat-sys-primary)', this.host);
    const accentSoft = resolveColor('color-mix(in srgb, var(--mat-sys-primary) 14%, transparent)', this.host);
    const muted = resolveColor('var(--app-muted)', this.host);
    const grid = resolveColor('var(--app-hairline)', this.host);
    const tooltipBg = resolveColor('var(--mat-sys-inverse-surface)', this.host);
    const tooltipText = resolveColor('var(--mat-sys-inverse-on-surface)', this.host);

    const data: ChartConfiguration['data'] = {
      labels: points.map((p) => p.label),
      datasets: [
        kind === 'bar'
          ? {
              type: 'bar',
              data: points.map((p) => p.value),
              backgroundColor: accent,
              hoverBackgroundColor: accent,
              borderRadius: { topLeft: 4, topRight: 4 },
              borderSkipped: 'start',
              maxBarThickness: 28,
              categoryPercentage: 0.82,
              barPercentage: 0.92,
            }
          : {
              type: 'line',
              data: points.map((p) => p.value),
              borderColor: accent,
              backgroundColor: accentSoft,
              borderWidth: 2,
              fill: 'origin',
              tension: 0.3,
              pointRadius: 0,
              pointHoverRadius: 5,
              pointHoverBorderWidth: 2,
              pointHoverBackgroundColor: accent,
              pointHoverBorderColor: resolveColor('var(--app-panel)', this.host),
            },
      ],
    };

    const options: ChartConfiguration['options'] = {
      responsive: true,
      maintainAspectRatio: false,
      animation: prefersReducedMotion() ? false : { duration: 400 },
      interaction: { mode: 'index', intersect: false },
      layout: { padding: { top: 8 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: tooltipBg,
          titleColor: tooltipText,
          bodyColor: tooltipText,
          padding: 10,
          cornerRadius: 8,
          displayColors: false,
          titleFont: { family: 'Geist Variable', weight: 500 },
          bodyFont: { family: 'Geist Variable' },
          callbacks: {
            title: (items) => points[items[0].dataIndex]?.fullLabel ?? '',
            label: (item) => `${formatNumber(item.parsed.y, decimals)} ${unit}`,
          },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: grid },
          ticks: {
            color: muted,
            font: { family: 'Geist Variable', size: 11 },
            maxRotation: 0,
            autoSkipPadding: 14,
          },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          grid: { color: grid },
          ticks: {
            color: muted,
            font: { family: 'Geist Variable', size: 11 },
            maxTicksLimit: 5,
            callback: (value) => formatNumber(Number(value), 1),
          },
        },
      },
    };

    return { data, options };
  });

  protected format(value: number): string {
    return formatNumber(value, this.decimals());
  }
}
