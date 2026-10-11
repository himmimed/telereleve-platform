import { bucketLabels, formatNumber, formatRelative } from './format';

const NOW = Date.parse('2026-10-11T12:00:00Z');

describe('French formatting', () => {
  it('formats numbers with a decimal comma', () => {
    expect(formatNumber(15234.75, 2)).toMatch(/^15\s234,75$/u);
  });

  it('shows a dash for missing values', () => {
    expect(formatNumber(null)).toBe('-');
  });

  it.each([
    [10, 'à l’instant'],
    [5 * 60, 'il y a 5 minutes'],
    [3 * 3600, 'il y a 3 heures'],
    [86_400, 'hier'],
  ])('describes %s seconds ago as "%s"', (seconds, expected) => {
    expect(formatRelative(new Date(NOW - seconds * 1000).toISOString(), NOW)).toBe(expected);
  });

  it('says "Jamais" when there is no date', () => {
    expect(formatRelative(null, NOW)).toBe('Jamais');
  });

  it('capitalises the full label of a day bucket', () => {
    const { fullLabel } = bucketLabels('2026-10-05T10:00:00Z', 'day');
    expect(fullLabel).toMatch(/^Lundi 5 octobre$/);
  });
});
