// Calendar of the platform: Moroccan legal time as a fixed UTC+1 offset, the same as the backend
// (docs/decisions.md D21). The browser's own time zone is never used: it may be UTC (servers, some
// embedded browsers) or Africa/Casablanca, which moves to UTC+0 during Ramadan. Either would shift the
// labels of the backend's day buckets by one day (D33).

/** IANA name of UTC+1 (the sign is inverted in POSIX zone names). */
export const PLATFORM_TIME_ZONE = 'Etc/GMT-1';

const OFFSET_MS = 3_600_000;

/** A Date whose UTC fields read as the platform's local time (internal helper). */
function toLocal(date: Date): Date {
  return new Date(date.getTime() + OFFSET_MS);
}

function fromLocal(local: Date): Date {
  return new Date(local.getTime() - OFFSET_MS);
}

/** Start of the platform hour `hoursBack` hours before `now`. */
export function startOfHour(now: Date, hoursBack = 0): Date {
  const local = toLocal(now);
  local.setUTCHours(local.getUTCHours() - hoursBack, 0, 0, 0);
  return fromLocal(local);
}

/** Platform midnight `daysBack` days before `now`'s day. */
export function startOfDay(now: Date, daysBack = 0): Date {
  const local = toLocal(now);
  local.setUTCDate(local.getUTCDate() - daysBack);
  local.setUTCHours(0, 0, 0, 0);
  return fromLocal(local);
}

/** First day of the platform month `monthsBack` months before `now`'s month, at midnight. */
export function startOfMonth(now: Date, monthsBack = 0): Date {
  const local = toLocal(now);
  local.setUTCMonth(local.getUTCMonth() - monthsBack, 1);
  local.setUTCHours(0, 0, 0, 0);
  return fromLocal(local);
}

export type PeriodKey = '24h' | '7d' | '30d' | '12m';

/** A consumption period ending now, starting on a whole bucket: 24 hours, 7 or 30 days, 12 months. */
export function periodRange(key: PeriodKey, now = new Date()): { from: string; to: string } {
  const from =
    key === '24h'
      ? startOfHour(now, 23)
      : key === '7d'
        ? startOfDay(now, 6)
        : key === '30d'
          ? startOfDay(now, 29)
          : startOfMonth(now, 11);
  return { from: from.toISOString(), to: now.toISOString() };
}
