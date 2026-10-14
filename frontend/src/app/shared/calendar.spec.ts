import { periodRange, startOfDay, startOfHour, startOfMonth } from './calendar';
import { bucketLabels, formatDateTime } from './format';

// 23:30 UTC on 14 October is already 00:30 on 15 October in Morocco (UTC+1): the cases where the
// browser's time zone and the platform calendar disagree.
const LATE_EVENING_UTC = new Date('2026-10-14T23:30:00Z');

describe('platform calendar (UTC+1)', () => {
  it('starts the day at 23:00 UTC the day before', () => {
    expect(startOfDay(LATE_EVENING_UTC).toISOString()).toBe('2026-10-14T23:00:00.000Z');
    expect(startOfDay(new Date('2026-10-14T22:59:00Z')).toISOString()).toBe('2026-10-13T23:00:00.000Z');
    expect(startOfDay(LATE_EVENING_UTC, 6).toISOString()).toBe('2026-10-08T23:00:00.000Z');
  });

  it('starts months on the 1st at local midnight, across years', () => {
    expect(startOfMonth(new Date('2026-10-31T23:30:00Z')).toISOString()).toBe('2026-10-31T23:00:00.000Z');
    expect(startOfMonth(new Date('2026-10-14T12:00:00Z'), 11).toISOString()).toBe('2025-10-31T23:00:00.000Z');
  });

  it('aligns hours', () => {
    expect(startOfHour(new Date('2026-10-14T10:47:12Z'), 23).toISOString()).toBe('2026-10-13T11:00:00.000Z');
  });

  it('builds the periods of the consumption charts on whole buckets', () => {
    expect(periodRange('7d', LATE_EVENING_UTC)).toEqual({
      from: '2026-10-08T23:00:00.000Z',
      to: '2026-10-14T23:30:00.000Z',
    });
    expect(periodRange('30d', LATE_EVENING_UTC).from).toBe('2026-09-15T23:00:00.000Z');
    expect(periodRange('12m', LATE_EVENING_UTC).from).toBe('2025-10-31T23:00:00.000Z');
    expect(periodRange('24h', LATE_EVENING_UTC).from).toBe('2026-10-14T00:00:00.000Z');
  });

  it('labels the backend day buckets with their Moroccan date, whatever the browser time zone', () => {
    // Bucket of 15 October in Morocco, as returned by the backend.
    expect(bucketLabels('2026-10-14T23:00:00Z', 'day').fullLabel).toBe('Jeudi 15 octobre');
    expect(bucketLabels('2026-10-14T23:00:00Z', 'hour').label).toBe('00:00');
    expect(bucketLabels('2026-10-31T23:00:00Z', 'month').fullLabel).toBe('Novembre 2026');
    expect(formatDateTime('2026-10-14T23:30:00Z')).toBe('15 oct. 2026, 00:30');
  });
});
