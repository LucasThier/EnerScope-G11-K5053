import type {
  ComparisonItem,
  ComparisonRequest,
  ComparisonResult,
  DifferenceAssessment,
  FindingType,
  MetricCategory,
  MetricCell,
  MetricRow,
  MetricUnit,
  SeriesPoint,
} from '../types/comparison';

/*
 * Pure helpers for the scenario comparison screen. No React, no API client and
 * no runtime imports, so they run under the Node test runner as they are.
 * Missing values are always shown as missing — never as zero.
 */

export const MIN_SCENARIOS = 2;
export const MAX_SCENARIOS = 10;
export const DEFAULT_RELEVANCE_PERCENT = 5;
export const DEFAULT_RATE_POINTS = 1;

export const CATEGORY_LABELS: Record<MetricCategory, string> = {
  FINANCIAL: 'Indicadores financieros',
  COST: 'Costos (CAPEX / OPEX)',
  CASH_FLOW: 'Flujo de caja',
  PHYSICAL: 'Resultados físicos',
};
const CATEGORY_ORDER: MetricCategory[] = ['FINANCIAL', 'COST', 'CASH_FLOW', 'PHYSICAL'];

export const ASSESSMENT_LABELS: Record<DifferenceAssessment, string> = {
  BASELINE: 'Línea base',
  EQUAL: 'Igual',
  BETTER: 'Mejor',
  WORSE: 'Peor',
  CHANGED: 'Distinto',
  NOT_COMPARABLE: 'No comparable',
};

const STATUS_LABELS: Record<string, string> = {
  NOT_AVAILABLE: 'Sin datos',
  NOT_RECOVERED_WITHIN_HORIZON: 'No se recupera en el horizonte',
  NOT_APPLICABLE: 'No aplica',
  NO_INVESTMENT: 'Sin inversión',
  NO_SIGN_CHANGE: 'Sin cambio de signo',
  NON_CONVENTIONAL: 'Flujo no convencional',
  NUMERICAL_FAILURE: 'Falla numérica',
  INSUFFICIENT_DATA: 'Datos insuficientes',
};

const NODE_TYPE_LABELS: Record<string, string> = {
  WELL: 'Pozo',
  GATHERING_NETWORK: 'Red de recolección',
  TREATMENT_PLANT: 'Planta de tratamiento',
  PIPELINE: 'Gasoducto',
  PIPELINE_CONECTION: 'Conexión de gasoducto',
  COMPRESSING_PLANT: 'Planta compresora',
  GROUND_LIQUEFACTION_PLANT: 'Planta de licuefacción en tierra',
  FLNG_UNIT: 'Unidad FLNG',
  SEAPORT_TERMINAL: 'Terminal portuaria',
  LNG_CAMER: 'Buque metanero',
  INTERNAL_CONSUMPTION: 'Consumo interno',
  INDUSTRIAL_CONSUMPTION: 'Consumo industrial',
};

/** Spanish name of a backend node type; unknown types are shown as they come. */
export function nodeTypeLabel(type: string | null | undefined): string | null {
  return type ? NODE_TYPE_LABELS[type] ?? type : null;
}

const NUMBER = (digits: number) =>
  new Intl.NumberFormat('es-AR', { minimumFractionDigits: 0, maximumFractionDigits: digits });

function finite(value: number | null | undefined): value is number {
  return value != null && Number.isFinite(value);
}

export function statusText(status: string): string {
  return STATUS_LABELS[status] ?? 'No disponible';
}

export function formatMetric(unit: MetricUnit, value: number | null | undefined, currency?: string | null): string {
  if (!finite(value)) return 'No disponible';
  switch (unit) {
    case 'CURRENCY':
      if (currency) {
        try {
          return new Intl.NumberFormat('es-AR', { style: 'currency', currency, currencyDisplay: 'code', maximumFractionDigits: 2 }).format(value);
        } catch {
          // An unknown currency code falls through to a plain number.
        }
      }
      return new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    case 'FRACTION':
      return new Intl.NumberFormat('es-AR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value);
    case 'YEARS':
      return NUMBER(1).format(value) + (value === 1 ? ' año' : ' años');
    case 'HOURS':
      return NUMBER(0).format(value) + ' h';
    case 'COUNT':
      return NUMBER(1).format(value);
    default:
      return NUMBER(2).format(value) + ' u.';
  }
}

/** The cell's value, or why there is none. */
export function cellText(row: Pick<MetricRow, 'unit'>, cell: Pick<MetricCell, 'value'>, currency?: string | null): string {
  return finite(cell.value.value) ? formatMetric(row.unit, cell.value.value, currency) : statusText(cell.value.status);
}

/** Probabilistic values are means; say over how many rounds when some rounds lack the indicator. */
export function roundsNote(cell: Pick<MetricCell, 'value'>): string | null {
  const { availableRounds, totalRounds, value } = cell.value;
  if (totalRounds <= 1 || !finite(value)) return null;
  return availableRounds === totalRounds ? `Media de ${totalRounds} rondas` : `Media de ${availableRounds} de ${totalRounds} rondas`;
}

function signed(text: string, value: number): string {
  if (value > 0) return '+' + text;
  if (value < 0) return '−' + text;
  return text;
}

/** Change against the baseline in the unit that decides relevance (p.p. for rates, years for paybacks, % otherwise). */
export function deltaText(unit: MetricUnit, cell: Pick<MetricCell, 'absoluteDelta' | 'relativeDelta' | 'assessment'>): string | null {
  if (cell.assessment === 'BASELINE' || !finite(cell.absoluteDelta)) return null;
  const delta = cell.absoluteDelta;
  if (delta === 0) return 'Sin cambios';
  if (unit === 'FRACTION') return signed(NUMBER(2).format(Math.abs(delta * 100)) + ' p.p.', delta);
  if (unit === 'YEARS') return signed(NUMBER(1).format(Math.abs(delta)) + (Math.abs(delta) === 1 ? ' año' : ' años'), delta);
  if (!finite(cell.relativeDelta)) return signed(NUMBER(2).format(Math.abs(delta)), delta) + ' (desde 0)';
  return signed(NUMBER(1).format(Math.abs(cell.relativeDelta * 100)) + ' %', delta);
}

export type Tone = 'good' | 'bad' | 'neutral' | 'muted';

export function assessmentTone(assessment: DifferenceAssessment): Tone {
  if (assessment === 'BETTER') return 'good';
  if (assessment === 'WORSE') return 'bad';
  if (assessment === 'CHANGED') return 'neutral';
  return 'muted';
}

export function findingTone(type: FindingType): Tone {
  if (type === 'SIGNIFICANT_IMPROVEMENT') return 'good';
  if (type === 'SIGNIFICANT_DETERIORATION' || type === 'NPV_SIGN_CHANGE') return 'bad';
  return 'neutral';
}

export const TONE_CLASSES: Record<Tone, string> = {
  good: 'border-brand-200 bg-brand-50 text-brand-800',
  bad: 'border-danger-200 bg-danger-50 text-danger-700',
  neutral: 'border-warn-200 bg-warn-50 text-warn-800',
  muted: 'border-ink-200 bg-ink-50 text-ink-600',
};

export interface MetricGroup { category: MetricCategory; label: string; rows: MetricRow[] }

/** Rows grouped by category in a fixed order; empty groups are dropped. */
export function groupMetrics(rows: MetricRow[], relevantOnly = false): MetricGroup[] {
  return CATEGORY_ORDER.map(category => ({
    category,
    label: CATEGORY_LABELS[category],
    rows: rows.filter(row => row.category === category && (!relevantOnly || row.relevant)),
  })).filter(group => group.rows.length > 0);
}

/** Version names, disambiguated with their position when two evaluations of one version are compared. */
export function scenarioNames(result: Pick<ComparisonResult, 'scenarios'>): Record<string, string> {
  const counts = new Map<string, number>();
  result.scenarios.forEach(s => counts.set(s.versionName, (counts.get(s.versionName) ?? 0) + 1));
  return Object.fromEntries(result.scenarios.map((s, index) =>
    [s.evaluationId, (counts.get(s.versionName) ?? 0) > 1 ? `${s.versionName} #${index + 1}` : s.versionName]));
}

// ---------------------------------------------------------------- chart

export type SeriesField = 'cumulativeCashFlow' | 'cashFlow' | 'producedVolume' | 'exportedVolume';

export const SERIES_FIELDS: { field: SeriesField; label: string; unit: MetricUnit }[] = [
  { field: 'cumulativeCashFlow', label: 'Flujo de caja acumulado', unit: 'CURRENCY' },
  { field: 'cashFlow', label: 'Flujo de caja anual', unit: 'CURRENCY' },
  { field: 'producedVolume', label: 'Volumen producido', unit: 'SIMULATOR_UNIT' },
  { field: 'exportedVolume', label: 'Volumen exportado', unit: 'SIMULATOR_UNIT' },
];

export interface ChartPoint { year: number; value: number }

/** Consecutive runs of present values: a missing year breaks the line instead of reading as zero. */
export function lineSegments(points: SeriesPoint[], field: SeriesField): ChartPoint[][] {
  const segments: ChartPoint[][] = [];
  let current: ChartPoint[] = [];
  let previousYear: number | null = null;
  for (const point of [...points].sort((a, b) => a.year - b.year)) {
    const value = point[field];
    const contiguous = previousYear !== null && point.year === previousYear + 1;
    if (!finite(value)) {
      if (current.length) segments.push(current);
      current = [];
    } else {
      if (current.length && !contiguous) {
        segments.push(current);
        current = [];
      }
      current.push({ year: point.year, value });
    }
    previousYear = point.year;
  }
  if (current.length) segments.push(current);
  return segments;
}

export interface ChartScale { minYear: number; maxYear: number; min: number; max: number; ticks: number[] }

/** Axis covering every present value and zero; null when there is nothing to draw. */
export function chartScale(series: SeriesPoint[][], field: SeriesField): ChartScale | null {
  const values: number[] = [];
  const years: number[] = [];
  for (const points of series) for (const point of points) {
    const value = point[field];
    if (finite(value)) { values.push(value); years.push(point.year); }
  }
  if (!values.length) return null;
  let min = Math.min(0, ...values);
  let max = Math.max(0, ...values);
  if (min === max) { max = min + 1; }
  const step = niceStep((max - min) / 4);
  min = Math.floor(min / step) * step;
  max = Math.ceil(max / step) * step;
  const ticks: number[] = [];
  for (let tick = min; tick <= max + step / 2; tick += step) ticks.push(Number(tick.toPrecision(12)));
  return { minYear: Math.min(...years), maxYear: Math.max(...years), min, max, ticks };
}

function niceStep(raw: number): number {
  const power = 10 ** Math.floor(Math.log10(raw));
  const fraction = raw / power;
  const nice = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return nice * power;
}

// ---------------------------------------------------------------- request

export interface DraftItem { versionId: string; evaluationId: string }

/** Why the draft cannot be compared yet, or null when it can. "" as evaluationId means "latest". */
export function draftError(items: DraftItem[], relevancePercent: string, ratePoints: string): string | null {
  if (items.length < MIN_SCENARIOS) return `Elegí al menos ${MIN_SCENARIOS} escenarios.`;
  if (items.length > MAX_SCENARIOS) return `Se pueden comparar hasta ${MAX_SCENARIOS} escenarios a la vez.`;
  if (items.some(item => !item.versionId)) return 'Elegí un escenario en cada fila.';
  const keys = items.map(item => item.versionId + '|' + (item.evaluationId || 'latest'));
  if (new Set(keys).size !== keys.length) return 'Hay dos filas con el mismo resultado. Elegí otra evaluación o quitá una fila.';
  for (const [value, label] of [[relevancePercent, 'El umbral de relevancia'], [ratePoints, 'El umbral para tasas']] as const) {
    if (value.trim() === '') continue;
    const number = Number(value.replace(',', '.'));
    if (!Number.isFinite(number) || number <= 0) return `${label} tiene que ser un número mayor que 0.`;
  }
  return null;
}

function threshold(value: string, scale: number): number | null {
  if (value.trim() === '') return null;
  return Number(value.replace(',', '.')) / scale;
}

/** Builds the API request: thresholds typed as % and p.p. become fractions; blank means the server default. */
export function toRequest(items: DraftItem[], relevancePercent: string, ratePoints: string,
                          name?: string, description?: string): ComparisonRequest {
  const request: ComparisonRequest = {
    relevanceThreshold: threshold(relevancePercent, 100),
    rateThreshold: threshold(ratePoints, 100),
    items: items.map((item): ComparisonItem => ({ versionId: item.versionId, evaluationId: item.evaluationId || null })),
  };
  if (name !== undefined) request.name = name.trim();
  if (description !== undefined) request.description = description.trim() || null;
  return request;
}

/** Inverse of {@link toRequest} for a saved comparison: fractions back to the % and p.p. the form shows. */
export function thresholdsForForm(relevance: number, rate: number): { relevancePercent: string; ratePoints: string } {
  const text = (value: number) => String(Number((value * 100).toPrecision(10)));
  return { relevancePercent: text(relevance), ratePoints: text(rate) };
}
