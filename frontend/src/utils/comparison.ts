import type { ComparisonRequest, ComparisonMetric, ComparisonCriterion } from '../types/comparison';

export const comparisonMetrics: { metric: ComparisonMetric; label: string; maximize: boolean; unit: string; description: string }[] = [
  { metric: 'NPV', label: 'VAN', maximize: true, unit: 'moneda', description: 'Valor actual neto guardado.' },
  { metric: 'IRR', label: 'TIR', maximize: true, unit: '%', description: 'Tasa anual en porcentaje. Requiere TIR definida en todas las rondas.' },
  { metric: 'PAYBACK', label: 'Recupero simple', maximize: false, unit: 'años', description: 'Período de recupero. Requiere recuperación en todas las rondas.' },
  { metric: 'INVESTMENT', label: 'Inversión', maximize: false, unit: 'moneda', description: 'Compras capitalizables devengadas dentro del horizonte y perímetro.' },
  { metric: 'OPERATING_COST', label: 'Costos operativos', maximize: false, unit: 'moneda', description: 'Egresos operativos de caja devengados; excluye inversión, depreciación, impuestos calculados y operaciones internas.' },
  { metric: 'DELIVERED_VOLUME', label: 'Volumen entregado', maximize: true, unit: 'unidad configurada', description: 'Salida acumulada en los puntos elegidos, con conversión explícita y sin puntos en serie.' },
];

export function comparisonErrors(request: ComparisonRequest): string[] {
  const errors: string[] = [];
  if (!request.name.trim() || !request.scopeDescription.trim())
    errors.push('Completá el nombre y la descripción del alcance y los contextos.');
  if (!request.assumptionsConfirmed)
    errors.push('Confirmá la equivalencia del alcance y la correspondencia de los contextos.');
  if (request.alternatives.length < 2)
    errors.push('Elegí al menos dos alternativas.');
  if (!request.contexts.length || request.contexts.some(c => !c.name.trim() || !Number.isFinite(c.probability) || c.probability <= 0) || Math.abs(request.contexts.reduce((s, c) => s + c.probability, 0) - 100) > 0.0000001)
    errors.push('Los contextos requieren nombre y probabilidades positivas que sumen 100 %.');
  if (request.alternatives.some(a => !a.name.trim() || a.cells.length !== request.contexts.length || a.cells.some(c => !c.versionId || !c.evaluationId)))
    errors.push('Completá el nombre y todas las evaluaciones de cada alternativa.');
  if (!request.criteria.length || request.criteria.some(c => !Number.isFinite(c.weight) || c.weight < 0) || Math.abs(request.criteria.reduce((s, c) => s + c.weight, 0) - 100) > 0.0000001)
    errors.push('Los pesos deben ser no negativos y sumar 100 %.');
  for (const c of request.criteria) {
    const meta = comparisonMetrics.find(m => m.metric === c.metric)!;
    if (![c.unfavorable, c.favorable].every(Number.isFinite) || (meta.maximize ? c.favorable <= c.unfavorable : c.favorable >= c.unfavorable))
      errors.push(`${meta.label}: completá referencias distintas y coherentes con ${meta.maximize ? 'maximizar' : 'minimizar'}.`);
    if ((c.minimum != null && !Number.isFinite(c.minimum)) || (c.maximum != null && !Number.isFinite(c.maximum)) || (c.minimum != null && c.maximum != null && c.minimum > c.maximum))
      errors.push(`${meta.label}: revisá los límites.`);
  }
  if (request.criteria.some(c => c.metric === 'DELIVERED_VOLUME') && request.alternatives.some(a => a.cells.some(c => !c.deliveryNodeIds.length)))
    errors.push('Seleccioná puntos de entrega en cada evaluación.');
  return errors;
}

export function newCriterion(metric: ComparisonMetric, weight = 0): ComparisonCriterion {
  return { metric, weight, unfavorable: NaN, favorable: NaN, minimum: null, maximum: null };
}

export function comparisonNumber(value: number | null): string {
  return value == null || !Number.isFinite(value) ? 'No disponible' : new Intl.NumberFormat('es-AR', { maximumFractionDigits: 4 }).format(value);
}
