import type { EconomicConfiguration } from '../types/economics';
type Path = (string | number)[];
// Update a single field without rebuilding contracts, schedules, ownership or unknown fields.
export function updateEconomicField(configuration: EconomicConfiguration, path: Path, value: unknown): EconomicConfiguration {
  const next = structuredClone(configuration);
  let target: unknown = next;
  for (const key of path.slice(0, -1)) target = (target as Record<string | number, unknown>)[key];
  (target as Record<string | number, unknown>)[path[path.length - 1]] = value;
  if (path[path.length - 1] === 'concept' && path[0] !== 'taxTreatments') {
    let original: unknown = configuration;
    for (const key of path) original = (original as Record<string | number, unknown>)[key];
    const previous = String(original), replacement = String(value);
    if (previous === replacement) return next;
    const used = [...next.adjustments, ...next.nodeProfiles.flatMap(p => p.rules), ...next.contracts.map(c => c.revenueRule), ...next.assets].some(item => item.concept === previous);
    const names = path[0] === 'assets' ? [[previous, replacement], [previous + '.depreciation', replacement + '.depreciation']] : [[previous, replacement]];
    for (const [oldName, newName] of names) {
      const treatments = next.taxTreatments.filter(t => t.concept === oldName).map(t => ({ ...t, concept: newName }));
      next.taxTreatments = used ? [...next.taxTreatments, ...treatments] : next.taxTreatments.map(t => t.concept === oldName ? { ...t, concept: newName } : t);
    }
  }
  return next;
}

