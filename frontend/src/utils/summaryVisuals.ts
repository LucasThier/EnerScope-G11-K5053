import type { NumericStatistics } from '../types/economics';
export const finiteValue = (value: unknown): value is number => typeof value === 'number' && Number.isFinite(value);
export interface BandPoint { period: number; label: string; values: NumericStatistics }
export function validBand(values: NumericStatistics) {
  return values.count > 0 && finiteValue(values.p10) && finiteValue(values.p50) && finiteValue(values.p90)
    && values.p10 <= values.p50 && values.p50 <= values.p90;
}
export function numericDomain(values: (number | null)[], includeZero = true): [number, number] {
  const valid = values.filter(finiteValue);
  if (!valid.length) return [0, 1];
  let low = Math.min(...valid), high = Math.max(...valid);
  if (includeZero) { low = Math.min(0, low); high = Math.max(0, high); }
  if (low === high) { const pad = Math.max(Math.abs(low) * .1, 1); low -= pad; high += pad; }
  return [low, high];
}
export function bandSegments(points: BandPoint[]) {
  const segments: BandPoint[][] = [];
  let current: BandPoint[] = [];
  for (const point of [...points].sort((a,b) => a.period-b.period)) {
    if (!validBand(point.values)) { current=[]; continue; }
    if (!current.length || point.period !== current[current.length-1].period+1) { current=[]; segments.push(current); }
    current.push(point);
  }
  return segments;
}
export function recoveryProbability(denominator: number, value: number | null) {
  return denominator > 0 && finiteValue(value) && value >= 0 && value <= 1 ? value : null;
}
