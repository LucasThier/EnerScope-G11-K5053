import type { NumericStatistics, PaybackStatistics } from '../types/economics';

export const statisticKeys = ['mean', 'min', 'max', 'p10', 'p50', 'p90'] as const;
export const finite = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
export interface BandPoint { period: number; label: string; values: NumericStatistics }

// Normalize first to keep even very large, finite monetary amounts on the canvas.
export function plotScale(values: unknown[], includeZero = false) {
  const valid = values.filter(finite);
  if (!valid.length) return null;
  if (includeZero) valid.push(0);
  const magnitude = Math.max(...valid.map(Math.abs)) || 1;
  let low = Math.min(...valid) / magnitude;
  let high = Math.max(...valid) / magnitude;
  const padding = high === low ? 0.1 : (high - low) * 0.08;
  low -= padding; high += padding;
  return { position: (value: number) => (value / magnitude - low) / (high - low) };
}
export function bandSegments(points: BandPoint[], fields: ('p10' | 'p50' | 'p90')[]) {
  const segments: BandPoint[][] = [];
  let current: BandPoint[] = [];
  for (const point of [...points].sort((a, b) => a.period - b.period)) {
    const valid = point.values.count > 0 && fields.every(key => finite(point.values[key]));
    if (!valid || (current.length && point.period !== current[current.length - 1].period + 1)) {
      if (current.length) segments.push(current);
      current = [];
    }
    if (valid) current.push(point);
  }
  if (current.length) segments.push(current);
  return segments;
}
export function noDispersion(points: BandPoint[]) {
  return points.length > 0 && points.every(({ values: v }) => v.count > 0 && finite(v.p10) && finite(v.p50) && finite(v.p90) && v.p10 === v.p50 && v.p50 === v.p90);
}
export function recoveryView(value: PaybackStatistics, startYear?: number) {
  const probability = value.denominator > 0 && finite(value.recoveredProbability) ? value.recoveredProbability : null;
  const period = value.recoveryPeriods.count > 0 && finite(value.recoveryPeriods.p50) ? value.recoveryPeriods.p50 : null;
  return { probability, period, year: period !== null && finite(startYear) ? startYear + period : null,
    notApplicable: value.statuses.NOT_APPLICABLE ?? 0,
    undetermined: Object.entries(value.statuses).filter(([s]) => !['RECOVERED', 'NOT_RECOVERED_WITHIN_HORIZON', 'NOT_APPLICABLE'].includes(s)).reduce((n, [, count]) => n + count, 0) };
}
