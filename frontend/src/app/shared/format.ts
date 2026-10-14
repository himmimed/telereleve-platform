// French formatting helpers shared by templates (through the pipes below) and charts.
import { Pipe, PipeTransform } from '@angular/core';
import { PLATFORM_TIME_ZONE } from './calendar';

const numberFormats = new Map<string, Intl.NumberFormat>();

export function formatNumber(value: number | null | undefined, maxDecimals = 1, minDecimals = 0): string {
  if (value === null || value === undefined || Number.isNaN(value)) {
    return '-';
  }
  const key = `${minDecimals}-${maxDecimals}`;
  let format = numberFormats.get(key);
  if (!format) {
    format = new Intl.NumberFormat('fr-FR', {
      minimumFractionDigits: minDecimals,
      maximumFractionDigits: maxDecimals,
    });
    numberFormats.set(key, format);
  }
  return format.format(value);
}

const relative = new Intl.RelativeTimeFormat('fr', { numeric: 'auto', style: 'long' });

/** "il y a 4 minutes", "hier", ... */
export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) {
    return 'Jamais';
  }
  const seconds = Math.round((Date.parse(iso) - now) / 1000);
  const abs = Math.abs(seconds);
  if (abs < 45) {
    return 'à l’instant';
  }
  if (abs < 3600) {
    return relative.format(Math.round(seconds / 60), 'minute');
  }
  if (abs < 86_400) {
    return relative.format(Math.round(seconds / 3600), 'hour');
  }
  if (abs < 30 * 86_400) {
    return relative.format(Math.round(seconds / 86_400), 'day');
  }
  return relative.format(Math.round(seconds / (30 * 86_400)), 'month');
}

const dateTime = new Intl.DateTimeFormat('fr-FR', { dateStyle: 'medium', timeStyle: 'short', timeZone: PLATFORM_TIME_ZONE });

export function formatDateTime(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '-';
}

const hourShort = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit', timeZone: PLATFORM_TIME_ZONE });
const hourFull = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', hour: '2-digit', minute: '2-digit', timeZone: PLATFORM_TIME_ZONE });
const dayShort = new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', timeZone: PLATFORM_TIME_ZONE });
const dayFull = new Intl.DateTimeFormat('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', timeZone: PLATFORM_TIME_ZONE });
const monthShort = new Intl.DateTimeFormat('fr-FR', { month: 'short', timeZone: PLATFORM_TIME_ZONE });
const monthFull = new Intl.DateTimeFormat('fr-FR', { month: 'long', year: 'numeric', timeZone: PLATFORM_TIME_ZONE });

/** Dates are shown on the platform calendar (UTC+1), like the backend's buckets (D33). */

/** Axis and tooltip labels of a time bucket ("14:00" / "lundi 6 octobre à 14:00"). */
export function bucketLabels(iso: string, step: 'hour' | 'day' | 'month'): { label: string; fullLabel: string } {
  const date = new Date(iso);
  switch (step) {
    case 'hour':
      return { label: hourShort.format(date), fullLabel: capitalise(hourFull.format(date)) };
    case 'day':
      return { label: dayShort.format(date), fullLabel: capitalise(dayFull.format(date)) };
    case 'month':
      return { label: monthShort.format(date), fullLabel: capitalise(monthFull.format(date)) };
  }
}

function capitalise(text: string): string {
  return text.charAt(0).toUpperCase() + text.slice(1);
}

@Pipe({ name: 'num' })
export class NumberPipe implements PipeTransform {
  transform(value: number | null | undefined, maxDecimals = 1, minDecimals = 0): string {
    return formatNumber(value, maxDecimals, minDecimals);
  }
}

@Pipe({ name: 'relative' })
export class RelativeTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatRelative(iso);
  }
}

@Pipe({ name: 'dateTime' })
export class DateTimePipe implements PipeTransform {
  transform(iso: string | null | undefined): string {
    return formatDateTime(iso);
  }
}
