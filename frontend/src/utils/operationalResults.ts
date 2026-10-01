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
