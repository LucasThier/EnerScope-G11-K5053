import type { AnnualNodeMetrics, Evaluation } from '../types/economics';

export type OperationalField = Exclude<keyof AnnualNodeMetrics, 'nodeId' | 'period'>;
export const operationalColumns: [OperationalField, string, string][] = [
  ['input', 'Entrada', 'u. sim.'], ['output', 'Producción / salida', 'u. sim.'],
  ['exported', 'Volumen exportado', 'u. sim.'], ['losses', 'Pérdidas', 'u. sim.'],
  ['operatingHours', 'Horas operativas', 'h'], ['events', 'Eventos', 'eventos'],
];
export function operationalNumber(value: number | null | undefined) {
  return typeof value === 'number' && Number.isFinite(value)
    ? new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value) : 'No disponible';
}
export function operationalNodes(evaluation: Evaluation) {
  const names = new Map((evaluation.snapshot.physicalNodes ?? []).map(n => [n.id, n.name]));
  return [...new Set((evaluation.snapshot.rawMetrics ?? []).map(m => m.nodeId))]
    .map(id => ({ id, name: names.get(id) || 'Nodo ' + id }))
    .sort((a, b) => a.name.localeCompare(b.name, 'es'));
}
export function operationalRows(evaluation: Evaluation, nodeId: string) {
  return (evaluation.snapshot.rawMetrics ?? []).filter(m => m.nodeId === nodeId)
    .slice().sort((a, b) => a.period - b.period);
}
export function operationalYear(evaluation: Evaluation, period: number) {
  const start = evaluation.snapshot.configuration?.startYear;
  return typeof start === 'number' && Number.isInteger(start) ? String(start + period) : 'Año no disponible';
}
// Missing years or values must never become a misleading complete-horizon total.
export function operationalTotal(rows: AnnualNodeMetrics[], field: OperationalField, years?: number) {
  if (!rows.length || (years != null && (rows.length !== years ||
      Array.from({ length: years }, (_, i) => i + 1).some(p => rows.filter(r => r.period === p).length !== 1)))) return null;
  if (rows.some(r => typeof r[field] !== 'number' || !Number.isFinite(r[field]))) return null;
  const total = rows.reduce((sum, r) => sum + (r[field] as number), 0);
  return Number.isFinite(total) ? total : null;
}

// Use only the immutable evaluation's conversions, never current editor values.
export function operationalDisplay(evaluation: Evaluation, nodeId: string) {
  const conversions = (evaluation.snapshot.configuration?.conversions ?? []).filter(c => c.nodeId === nodeId &&
    ['INPUT_VOLUME', 'OUTPUT_VOLUME', 'EXPORTED_VOLUME'].includes(c.metric) && c.unit.trim() && Number.isFinite(c.factor) && c.factor > 0);
  const common = conversions.length && conversions.every(c => c.unit === conversions[0].unit && c.factor === conversions[0].factor) ? conversions[0] : undefined;
  const metrics = { input: 'INPUT_VOLUME', output: 'OUTPUT_VOLUME', exported: 'EXPORTED_VOLUME', losses: '' };
  const units: Record<OperationalField, string> = { input: 'u. sim.', output: 'u. sim.', exported: 'u. sim.', losses: 'u. sim.', operatingHours: 'h', events: 'eventos' };
  const factors: Partial<Record<OperationalField, number>> = {};
  for (const field of ['input', 'output', 'exported', 'losses'] as const) {
    const matches = conversions.filter(c => c.metric === metrics[field]);
    const conversion = matches.length ? (matches.every(c => c.unit === matches[0].unit && c.factor === matches[0].factor) ? matches[0] : undefined) : common;
    if (conversion) { units[field] = conversion.unit; factors[field] = conversion.factor; }
  }
  const rows = operationalRows(evaluation, nodeId).map(row => {
    const result = { ...row };
    for (const field of ['input', 'output', 'exported', 'losses'] as const) {
      const value = row[field];
      if (value != null) { const converted = value * (factors[field] ?? 1); result[field] = Number.isFinite(converted) ? converted : null; }
    }
    return result;
  });
  return { rows, units };
}
