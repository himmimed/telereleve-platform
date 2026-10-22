import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input } from '@angular/core';
import {
  BarController,
  BarElement,
  CategoryScale,
  ChartConfiguration,
  ChartTypeRegistry,
  Filler,
  LineController,
  LineElement,
  LinearScale,
  PointElement,
  Plugin,
  ScriptableContext,
  Tooltip,
} from 'chart.js';
import { BaseChartDirective, provideCharts } from 'ng2-charts';
import { ThemeService, resolveColor } from '../core/theme.service';
import { formatNumber } from './format';
import { prefersReducedMotion } from './motion';

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
      <canvas baseChart [type]="kind()" [data]="config().data" [options]="config().options" [plugins]="config().plugins" role="img" [attr.aria-label]="ariaLabel()"></canvas>
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

  /** Identifies the series being shown, so the grow-in plays when it changes but not on a plain refresh. */
  private shape = '';

  protected readonly config = computed(() => {
    // Depend on the theme so colours are re-resolved when it changes.
    this.theme.isDark();
    const points = this.points();
    const unit = this.unit();
    const decimals = this.decimals();
    const kind = this.kind();
    const shape = `${kind}|${unit}|${points.length}|${points[0]?.label ?? ''}`;
    const entering = shape !== this.shape;
    this.shape = shape;

    const accent = resolveColor('var(--mat-sys-primary)', this.host);
    const accentDeep = resolveColor('var(--ceac-blue-deep)', this.host);
    // Area under the line: brand blue fading to nothing, so the curve reads first and the fill second.
    const fillTop = resolveColor('color-mix(in srgb, var(--mat-sys-primary) 24%, transparent)', this.host);
    const fillBottom = resolveColor('color-mix(in srgb, var(--mat-sys-primary) 0%, transparent)', this.host);
    const areaFill = (context: ScriptableContext<'line'>) => {
      const { ctx, chartArea } = context.chart;
      if (!chartArea) {
        return fillTop;
      }
      const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
      gradient.addColorStop(0, fillTop);
      gradient.addColorStop(1, fillBottom);
      return gradient;
    };
    const halo = resolveColor('color-mix(in srgb, var(--mat-sys-primary) 20%, transparent)', this.host);
    const guide = resolveColor('color-mix(in srgb, var(--mat-sys-primary) 38%, transparent)', this.host);
    const surface = resolveColor('var(--app-panel)', this.host);
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
              hoverBackgroundColor: accentDeep,
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
              backgroundColor: areaFill,
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

    // Line chart only: a hairline under the pointer and a ringed dot on the latest value, so the eye lands on
    // "where are we now" before reading the axis.
    const readout: Plugin = {
      id: 'readout',
      beforeDatasetsDraw: (chart) => {
        const active = chart.tooltip?.getActiveElements();
        if (kind !== 'line' || !active?.length) {
          return;
        }
        const { ctx, chartArea } = chart;
        ctx.save();
        ctx.strokeStyle = guide;
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(Math.round(active[0].element.x) + 0.5, chartArea.top);
        ctx.lineTo(Math.round(active[0].element.x) + 0.5, chartArea.bottom);
        ctx.stroke();
        ctx.restore();
      },
      afterDatasetsDraw: (chart) => {
        const last = chart.getDatasetMeta(0).data.at(-1);
        if (kind !== 'line' || !last) {
          return;
        }
        const { ctx } = chart;
        ctx.save();
        ctx.fillStyle = halo;
        ctx.beginPath();
        ctx.arc(last.x, last.y, 9, 0, Math.PI * 2);
        ctx.fill();
        ctx.fillStyle = accent;
        ctx.strokeStyle = surface;
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.arc(last.x, last.y, 4.5, 0, Math.PI * 2);
        ctx.fill();
        ctx.stroke();
        ctx.restore();
      },
    };

    // Left to right: each value rises from the baseline a few milliseconds after its neighbour.
    const wave = (ctx: ScriptableContext<keyof ChartTypeRegistry>) =>
      ctx.type === 'data' ? ctx.dataIndex * (kind === 'line' ? 14 : 20) : 0;

    const options: ChartConfiguration['options'] = {
      responsive: true,
      maintainAspectRatio: false,
      // A new series (first arrival, other energy or period): values grow from the baseline in a wave. A plain
      // refresh only glides what changed, so the chart does not replay its entrance every 30 s.
      animation: prefersReducedMotion()
        ? false
        : { duration: entering ? 900 : 480, easing: entering ? 'easeOutQuart' : 'easeOutCubic' },
      animations:
        prefersReducedMotion() || !entering
          ? {}
          : {
              y: {
                from: (ctx: ScriptableContext<keyof ChartTypeRegistry>) => ctx.chart.scales['y'].getPixelForValue(0),
                delay: wave,
              },
            },
      interaction: { mode: 'index', intersect: false },
      layout: { padding: { top: 8, right: 12 } },
      plugins: {
        legend: { display: false },
        tooltip: {
          backgroundColor: tooltipBg,
          titleColor: tooltipText,
          bodyColor: tooltipText,
          padding: 10,
          cornerRadius: 6,
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
          border: { display: false, dash: [3, 5] },
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

    return { data, options, plugins: [readout] };
  });

  protected format(value: number): string {
    return formatNumber(value, this.decimals());
  }
}
