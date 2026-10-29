import type { Payback } from '../types/economics';

export function cashFlowScale(values: number[]) {
  const finite = values.filter(Number.isFinite);
  const low = Math.min(0, ...finite), high = Math.max(0, ...finite);
  const range = high - low || 1;
  const magnitude = 10 ** Math.floor(Math.log10(range / 5));
  const step = [1, 2, 5, 10].map(n => n * magnitude).find(n => n >= range / 5) ?? magnitude * 10;
  const min = Math.floor(low / step) * step;
  const max = Math.ceil(high / step) * step || (min === 0 ? 1 : 0);
  const y = (value: number) => 24 + (max - value) / (max - min) * 232;
  const count = Math.round((max - min) / step);
  const ticks = Array.from({ length: Math.min(count + 1, 12) }, (_, i) => min + i * step);
  return { min, max, ticks, y, zero: y(0) };
}

export function recoveryMarkers(simple: Payback | undefined, discounted: Payback | undefined, years: number) {
  const groups = new Map<number, string[]>();
  for (const [label, value] of [['Simple', simple], ['Descontada', discounted]] as const) {
    if (value?.status !== 'RECOVERED' || value.period == null || !Number.isInteger(value.period) || value.period < 0 || value.period > years) continue;
    groups.set(value.period, [...(groups.get(value.period) ?? []), label]);
  }
  return [...groups].sort(([a], [b]) => a - b).map(([period, labels]) => ({ period, label: labels.length === 2 ? 'Simple y descontada' : labels[0] }));
}
