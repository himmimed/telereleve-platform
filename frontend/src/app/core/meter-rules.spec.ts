import { isSilent, isVoltageInRange, meterHealth, signalQuality } from './meter-rules';

const NOW = Date.parse('2026-10-11T12:00:00Z');
const minutesAgo = (minutes: number) => new Date(NOW - minutes * 60_000).toISOString();

describe('meter rules', () => {
  describe('isSilent (A3: no reading for more than 3 x interval)', () => {
    const meter = { status: 'ACTIVE' as const, readingIntervalMin: 15 };

    it('is not silent exactly at 3 x the interval', () => {
      expect(isSilent({ ...meter, lastReadingAt: minutesAgo(45) }, NOW)).toBe(false);
    });

    it('is silent just after 3 x the interval', () => {
      expect(isSilent({ ...meter, lastReadingAt: minutesAgo(45.1) }, NOW)).toBe(true);
    });

    it('is silent when the meter never reported', () => {
      expect(isSilent({ ...meter, lastReadingAt: null }, NOW)).toBe(true);
    });

    it('never flags an inactive meter', () => {
      expect(isSilent({ ...meter, status: 'INACTIVE', lastReadingAt: minutesAgo(600) }, NOW)).toBe(false);
    });

    it('uses the meter own interval', () => {
      expect(isSilent({ ...meter, readingIntervalMin: 60, lastReadingAt: minutesAgo(120) }, NOW)).toBe(false);
    });
  });

  describe('meterHealth', () => {
    const meter = { status: 'ACTIVE' as const, readingIntervalMin: 15, lastReadingAt: minutesAgo(5) };

    it('is OK with recent readings and no open alert', () => {
      expect(meterHealth(meter, 0, NOW)).toBe('OK');
    });

    it('is ALERT when an alert is open', () => {
      expect(meterHealth(meter, 2, NOW)).toBe('ALERT');
    });

    it('reports SILENT before ALERT', () => {
      expect(meterHealth({ ...meter, lastReadingAt: minutesAgo(90) }, 1, NOW)).toBe('SILENT');
    });
  });

  describe('isVoltageInRange (A5: 230 V ± 10 %)', () => {
    it.each([207, 230, 253])('accepts %s V', (voltage) => {
      expect(isVoltageInRange(voltage)).toBe(true);
    });

    it.each([206.9, 253.1, 0])('rejects %s V', (voltage) => {
      expect(isVoltageInRange(voltage)).toBe(false);
    });
  });

  describe('signalQuality', () => {
    it.each([
      [-60, 'Excellent'],
      [-65, 'Excellent'],
      [-66, 'Bon'],
      [-75, 'Bon'],
      [-80, 'Moyen'],
      [-86, 'Faible'],
    ] as const)('%s dBm is %s', (dbm, quality) => {
      expect(signalQuality(dbm)).toBe(quality);
    });
  });
});
