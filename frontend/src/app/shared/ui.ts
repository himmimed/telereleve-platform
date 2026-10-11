// Small presentational building blocks used across pages.
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import {
  ALERT_STATUS_LABEL,
  MAP_STATUS_LABEL,
  METER_TYPE_ICON,
  METER_TYPE_LABEL,
  SEVERITY_LABEL,
} from '../core/labels';
import { AlertStatus, MapStatus, MeterType, Severity } from '../core/models';

export type Tone = 'ok' | 'alert' | 'silent' | 'critical' | 'high' | 'medium' | 'accent' | 'neutral';

/** Tinted label for a state. Colour is never the only signal: the label is always written out. */
@Component({
  selector: 'app-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    @if (icon()) {
      <mat-icon fontSet="ph" [fontIcon]="icon()!" aria-hidden="true" />
    }
    <span>{{ label() }}</span>
  `,
  styles: `
    :host {
      --tone: var(--app-muted);
      display: inline-flex;
      align-items: center;
      gap: 4px;
      height: 22px;
      padding: 0 8px;
      border-radius: var(--radius-badge);
      background: color-mix(in srgb, var(--tone) 13%, transparent);
      color: color-mix(in srgb, var(--tone) 82%, var(--mat-sys-on-surface));
      font: var(--mat-sys-label-medium);
      font-weight: 550;
      white-space: nowrap;
    }
    :host(.tone-ok) { --tone: var(--status-ok); }
    :host(.tone-alert) { --tone: var(--status-alert); }
    :host(.tone-silent) { --tone: var(--status-silent); }
    :host(.tone-critical) { --tone: var(--sev-critical); }
    :host(.tone-high) { --tone: var(--sev-high); }
    :host(.tone-medium) { --tone: var(--sev-medium); }
    :host(.tone-accent) { --tone: var(--mat-sys-primary); }
    mat-icon.mat-icon { font-size: 14px; width: 14px; height: 14px; line-height: 14px; }
  `,
  host: { '[class]': '"tone-" + tone()' },
})
export class Badge {
  readonly label = input.required<string>();
  readonly tone = input<Tone>('neutral');
  readonly icon = input<string | null>(null);
}

const MAP_TONE: Record<MapStatus, Tone> = { OK: 'ok', ALERT: 'alert', SILENT: 'silent' };
const MAP_ICON: Record<MapStatus, string> = {
  OK: 'ph-check-circle',
  ALERT: 'ph-warning',
  SILENT: 'ph-wifi-slash',
};

@Component({
  selector: 'app-map-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge],
  template: `<app-badge [label]="label()" [tone]="tone()" [icon]="icon()" />`,
})
export class MapStatusBadge {
  readonly status = input.required<MapStatus>();
  protected readonly label = computed(() => MAP_STATUS_LABEL[this.status()]);
  protected readonly tone = computed(() => MAP_TONE[this.status()]);
  protected readonly icon = computed(() => MAP_ICON[this.status()]);
}

const SEVERITY_TONE: Record<Severity, Tone> = { CRITICAL: 'critical', HIGH: 'high', MEDIUM: 'medium' };
const SEVERITY_ICON: Record<Severity, string> = {
  CRITICAL: 'ph-warning-octagon',
  HIGH: 'ph-warning',
  MEDIUM: 'ph-info',
};

@Component({
  selector: 'app-severity',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge],
  template: `<app-badge [label]="label()" [tone]="tone()" [icon]="icon()" />`,
})
export class SeverityBadge {
  readonly severity = input.required<Severity>();
  protected readonly label = computed(() => SEVERITY_LABEL[this.severity()]);
  protected readonly tone = computed(() => SEVERITY_TONE[this.severity()]);
  protected readonly icon = computed(() => SEVERITY_ICON[this.severity()]);
}

const STATUS_TONE: Record<AlertStatus, Tone> = { NEW: 'accent', IN_PROGRESS: 'neutral', RESOLVED: 'ok' };

@Component({
  selector: 'app-alert-status',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Badge],
  template: `<app-badge [label]="label()" [tone]="tone()" />`,
})
export class AlertStatusBadge {
  readonly status = input.required<AlertStatus>();
  protected readonly label = computed(() => ALERT_STATUS_LABEL[this.status()]);
  protected readonly tone = computed(() => STATUS_TONE[this.status()]);
}

/** Energy type: icon + label. */
@Component({
  selector: 'app-meter-type',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <mat-icon fontSet="ph" [fontIcon]="icon()" aria-hidden="true" />
    @if (!compact()) {
      <span>{{ label() }}</span>
    } @else {
      <span class="visually-hidden">{{ label() }}</span>
    }
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 6px; }
    mat-icon.mat-icon { font-size: 16px; width: 16px; height: 16px; line-height: 16px; color: var(--app-muted); }
    :host(.water) mat-icon { color: light-dark(#0369a1, #38bdf8); }
    :host(.electricity) mat-icon { color: light-dark(#a16207, #facc15); }
  `,
  host: { '[class.water]': 'type() === "WATER"', '[class.electricity]': 'type() === "ELECTRICITY"' },
})
export class MeterTypeLabel {
  readonly type = input.required<MeterType>();
  readonly compact = input(false);
  protected readonly icon = computed(() => METER_TYPE_ICON[this.type()]);
  protected readonly label = computed(() => METER_TYPE_LABEL[this.type()]);
}

/** Placeholder block shaped like the content it replaces while data loads. */
@Component({
  selector: 'app-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '',
  styles: `
    :host {
      display: block;
      border-radius: 6px;
      background: linear-gradient(
        90deg,
        color-mix(in srgb, var(--mat-sys-on-surface) 6%, transparent) 0%,
        color-mix(in srgb, var(--mat-sys-on-surface) 11%, transparent) 50%,
        color-mix(in srgb, var(--mat-sys-on-surface) 6%, transparent) 100%
      );
      background-size: 200% 100%;
      animation: shimmer 1.4s ease-in-out infinite;
    }
    @keyframes shimmer {
      from { background-position: 100% 0; }
      to { background-position: -100% 0; }
    }
  `,
  host: { '[style.height]': 'height()', '[style.width]': 'width()', 'aria-hidden': 'true' },
})
export class Skeleton {
  readonly height = input('14px');
  readonly width = input('100%');
}

@Component({
  selector: 'app-empty-state',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatIconModule],
  template: `
    <mat-icon fontSet="ph" [fontIcon]="icon()" aria-hidden="true" />
    <p class="title">{{ title() }}</p>
    @if (text()) {
      <p class="text">{{ text() }}</p>
    }
    <ng-content />
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 6px;
      padding: 40px 16px;
      text-align: center;
    }
    mat-icon.mat-icon {
      font-size: 28px; width: 28px; height: 28px; line-height: 28px;
      color: var(--app-muted);
      margin-bottom: 4px;
    }
    .title { font: var(--mat-sys-title-small); font-weight: 600; }
    .text { color: var(--app-muted); max-width: 42ch; }
  `,
})
export class EmptyState {
  readonly icon = input('ph-tray');
  readonly title = input.required<string>();
  readonly text = input<string | null>(null);
}

@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="titles">
      <h1>{{ title() }}</h1>
      @if (subtitle()) {
        <p class="muted">{{ subtitle() }}</p>
      }
    </div>
    <div class="actions"><ng-content /></div>
  `,
  styles: `
    :host {
      display: flex;
      align-items: flex-end;
      justify-content: space-between;
      gap: 16px;
      flex-wrap: wrap;
      margin-bottom: 20px;
    }
    h1 { font: var(--mat-sys-headline-small); font-weight: 600; }
    p { margin-top: 4px; }
    .actions { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
  `,
})
export class PageHeader {
  readonly title = input.required<string>();
  readonly subtitle = input<string | null>(null);
}
