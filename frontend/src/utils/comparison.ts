import type {
  ComparableScenario,
  ComparisonMetric,
  ComparisonRequest,
  ComparisonResult,
  ComparedScenario,
  FindingType,
  MetricCategory,
  MetricCell,
  MetricRow,
  MetricUnit,
  SeriesPoint,
  Standing,
} from '../types/comparison';

/*
 * Pure helpers for the scenario comparison screens. No React, no API client and
 * no runtime imports, so they run under the Node test runner as they are.
 * Missing values are always shown as missing — never as zero.
 */

export const MIN_SCENARIOS = 2;
/** Route id of a result that was not saved (read-only members compare without saving). */
export const PREVIEW_ID = 'sin-guardar';
/** The categorical palette identifies eight scenarios; the backend enforces the same cap. */
export const MAX_SCENARIOS = 8;

export const CATEGORY_LABELS: Record<MetricCategory, string> = {
  FINANCIAL: 'Indicadores financieros',
  COST: 'Costos (CAPEX / OPEX)',
  CASH_FLOW: 'Flujo de caja',
  PHYSICAL: 'Resultados físicos',
};
const CATEGORY_ORDER: MetricCategory[] = ['FINANCIAL', 'COST', 'CASH_FLOW', 'PHYSICAL'];

export const STANDING_LABELS: Record<Standing, string> = {
  BEST: 'El mejor',
  MIDDLE: 'Intermedio',
  WORST: 'El más bajo',
  TIED: 'Igual en todos',
  UNRANKED: 'Sin posición',
  NOT_COMPARABLE: 'No comparable',
};

/** The four numbers a decision starts from, shown first and biggest. */
export const KEY_METRICS: ComparisonMetric[] = ['NPV', 'IRR', 'SIMPLE_PAYBACK', 'CAPEX'];
/** The next layer: still visual, smaller. Everything else lives in the detail table. */
export const SECONDARY_METRICS: ComparisonMetric[] = [
  'ROI', 'ROE', 'DISCOUNTED_PAYBACK', 'OPEX', 'REVENUE', 'NET_CASH_FLOW', 'PRODUCED_VOLUME', 'EXPORTED_VOLUME',
];

/** Plain-language titles and one-line meanings for readers without a finance background. */
export const METRIC_COPY: Partial<Record<ComparisonMetric, { title: string; meaning: string }>> = {
  NPV: { title: 'Valor que genera (VAN)', meaning: 'Ganancia del proyecto llevada a hoy. Más alto es mejor; negativo significa que pierde valor.' },
  IRR: { title: 'Rentabilidad anual (TIR)', meaning: 'Rendimiento anual de lo invertido. Más alto es mejor.' },
  SIMPLE_PAYBACK: { title: 'Recupero de la inversión', meaning: 'Años hasta recuperar lo invertido. Menos es mejor.' },
  CAPEX: { title: 'Inversión inicial (CAPEX)', meaning: 'Cuánto hay que invertir en equipos e instalaciones. Menos es mejor.' },
  ROI: { title: 'Retorno sobre la inversión (ROI)', meaning: 'Flujo neto obtenido por cada peso invertido.' },
  ROE: { title: 'Retorno anual sobre el capital (ROE)', meaning: 'Resultado anual promedio sobre el capital invertido.' },
  DISCOUNTED_PAYBACK: { title: 'Recupero descontado', meaning: 'Años hasta recuperar la inversión, contando el costo del dinero.' },
  OPEX: { title: 'Costos operativos (OPEX)', meaning: 'Gastos de operación en todo el horizonte.' },
  REVENUE: { title: 'Ingresos', meaning: 'Ventas cobradas en todo el horizonte.' },
  NET_CASH_FLOW: { title: 'Flujo de caja neto', meaning: 'Lo que entra menos lo que sale, en todo el horizonte.' },
  PRODUCED_VOLUME: { title: 'Volumen producido', meaning: 'Producción total en origen (unidades del simulador).' },
  EXPORTED_VOLUME: { title: 'Volumen exportado', meaning: 'Volumen total exportado (unidades del simulador).' },
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

/**
 * Short form for headline numbers and bars: whole units with separators below a million
 * ("USD 1.233", "USD 815"; cents only under 100) and "M" from a million up ("USD 1,3 M",
 * "45,6 M u."). Spanish has no natural short form for thousands, so "1,2 K" is avoided.
 */
export function compactMetric(unit: MetricUnit, value: number | null | undefined, currency?: string | null): string {
  if (!finite(value)) return 'No disponible';
  if (unit === 'FRACTION' || unit === 'YEARS') return formatMetric(unit, value, currency);
  const size = Math.abs(value);
  const compact = size >= 1_000_000
    ? new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 }).format(value)
    : NUMBER(size >= 100 ? 0 : 2).format(value);
  if (unit === 'CURRENCY') return (currency ? currency + ' ' : '') + compact;
  if (unit === 'HOURS') return compact + ' h';
  if (unit === 'COUNT') return compact;
  return compact + ' u.';
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

/**
 * How far a value is from the best one, in plain words and in the indicator's unit:
 * "USD 514 menos que el mejor", "1 año más que el mejor", "8,73 p.p. menos que el mejor".
 * The best one reads "El mejor"; unranked values have no text.
 */
export function gapText(row: Pick<MetricRow, 'unit' | 'preference'>, cell: Pick<MetricCell, 'standing' | 'gapToBest'>,
                        currency?: string | null): string | null {
  if (cell.standing === 'BEST') return 'El mejor';
  if (cell.standing === 'TIED') return 'Igual en todos';
  if ((cell.standing !== 'MIDDLE' && cell.standing !== 'WORST') || !finite(cell.gapToBest)) return null;
  const gap = cell.gapToBest;
  const direction = row.preference === 'LOWER_IS_BETTER' ? 'más' : 'menos';
  const amount = row.unit === 'FRACTION' ? NUMBER(2).format(gap * 100) + ' p.p.'
    : row.unit === 'YEARS' ? NUMBER(1).format(gap) + (gap === 1 ? ' año' : ' años')
    : compactMetric(row.unit, gap, currency);
  return `${amount} ${direction} que el mejor`;
}

/** "1.º", "2.º"… for ranked values. */
export function rankLabel(rank: number | null | undefined): string | null {
  return rank == null ? null : `${rank}.º`;
}

export type Tone = 'good' | 'bad' | 'neutral' | 'muted';

export function standingTone(standing: Standing): Tone {
  if (standing === 'BEST') return 'good';
  if (standing === 'WORST') return 'bad';
  return 'muted';
}

/** Losing value or never recovering the investment is a warning; other gaps are information. */
export function findingTone(type: FindingType): Tone {
  if (type === 'NPV_SIGN_SPLIT' || type === 'AVAILABILITY_SPLIT') return 'bad';
  if (type === 'LARGE_GAP') return 'neutral';
  return 'muted';
}

export const TONE_CLASSES: Record<Tone, string> = {
  good: 'border-brand-200 bg-brand-50 text-brand-800',
  bad: 'border-danger-200 bg-danger-50 text-danger-700',
  neutral: 'border-warn-200 bg-warn-50 text-warn-800',
  muted: 'border-ink-200 bg-ink-50 text-ink-600',
};

/**
 * Scenario identity colours, in the validated categorical order. Spelled out in full
 * so Tailwind generates them. Identity only: text stays in ink tokens.
 */
const SERIES = [
  { bg: 'bg-series-1', fill: 'fill-series-1', stroke: 'stroke-series-1', border: 'border-series-1' },
  { bg: 'bg-series-2', fill: 'fill-series-2', stroke: 'stroke-series-2', border: 'border-series-2' },
  { bg: 'bg-series-3', fill: 'fill-series-3', stroke: 'stroke-series-3', border: 'border-series-3' },
  { bg: 'bg-series-4', fill: 'fill-series-4', stroke: 'stroke-series-4', border: 'border-series-4' },
  { bg: 'bg-series-5', fill: 'fill-series-5', stroke: 'stroke-series-5', border: 'border-series-5' },
  { bg: 'bg-series-6', fill: 'fill-series-6', stroke: 'stroke-series-6', border: 'border-series-6' },
  { bg: 'bg-series-7', fill: 'fill-series-7', stroke: 'stroke-series-7', border: 'border-series-7' },
  { bg: 'bg-series-8', fill: 'fill-series-8', stroke: 'stroke-series-8', border: 'border-series-8' },
];

/** The colour of the scenario at `index` (selection order). Never cycled: the cap is eight. */
export function seriesColor(index: number) {
  return SERIES[Math.min(Math.max(index, 0), SERIES.length - 1)];
}

export interface MetricGroup { category: MetricCategory; label: string; rows: MetricRow[] }

/** Rows grouped by category in a fixed order; empty groups are dropped. */
export function groupMetrics(rows: MetricRow[], relevantOnly = false): MetricGroup[] {
  return CATEGORY_ORDER.map(category => ({
    category,
    label: CATEGORY_LABELS[category],
    rows: rows.filter(row => row.category === category && (!relevantOnly || row.relevant)),
  })).filter(group => group.rows.length > 0);
}

/** The label of a deleted scenario in a saved comparison. */
export const DELETED_SUFFIX = ' (eliminado)';
export const DELETED_NOTE = 'se eliminó el escenario o su versión después de guardar esta comparación; se muestran los datos que tenía guardados.';

/** "Versión · Escenario", or just the version for results saved before scenarios existed. */
export function scenarioLabel(scenario: { versionName: string; scenarioName?: string | null }): string {
  return scenario.scenarioName ? `${scenario.versionName} · ${scenario.scenarioName}` : scenario.versionName;
}

/**
 * The names a result shows, as the backend writes them in its findings: the scenario name when it is distinct among the
 * compared scenarios, otherwise "versión · escenario" (default scenarios of different versions share a name), the version
 * name for results saved before scenarios existed, and the position when two evaluations of one scenario are compared.
 * A scenario deleted since the comparison was saved carries "(eliminado)".
 */
export function scenarioNames(result: { scenarios: Pick<ComparedScenario, 'evaluationId' | 'versionName' | 'scenarioName' | 'deleted'>[] }): Record<string, string> {
  const nameCounts = new Map<string, number>();
  result.scenarios.forEach(s => nameCounts.set(String(s.scenarioName), (nameCounts.get(String(s.scenarioName)) ?? 0) + 1));
  const base = result.scenarios.map(s => {
    if (!s.scenarioName) return s.versionName;
    return (nameCounts.get(s.scenarioName) ?? 0) > 1 ? `${s.versionName} · ${s.scenarioName}` : s.scenarioName;
  });
  const counts = new Map<string, number>();
  base.forEach(name => counts.set(name, (counts.get(name) ?? 0) + 1));
  return Object.fromEntries(result.scenarios.map((s, index) => {
    const name = (counts.get(base[index]) ?? 0) > 1 ? `${base[index]} #${index + 1}` : base[index];
    return [s.evaluationId, s.deleted ? name + DELETED_SUFFIX : name];
  }));
}

/** How many of the compared scenarios were deleted after the comparison was saved. */
export function deletedCount(result: { scenarios: Pick<ComparedScenario, 'deleted'>[] }): number {
  return result.scenarios.filter(s => s.deleted).length;
}

export interface VersionGroup { versionId: string; versionName: string; scenarios: ComparableScenario[] }

/** The picker's scenarios grouped by version, in the order the server lists them (versions oldest first). */
export function groupByVersion(scenarios: ComparableScenario[]): VersionGroup[] {
  const groups = new Map<string, VersionGroup>();
  for (const scenario of scenarios) {
    const group = groups.get(scenario.versionId) ?? { versionId: scenario.versionId, versionName: scenario.versionName, scenarios: [] };
    group.scenarios.push(scenario);
    groups.set(scenario.versionId, group);
  }
  return [...groups.values()];
}

/** The picker's search: matches the scenario name or its version's name. */
export function filterScenarios(scenarios: ComparableScenario[], query: string): ComparableScenario[] {
  const text = query.trim().toLocaleLowerCase('es');
  if (!text) return scenarios;
  return scenarios.filter(s => s.scenarioName.toLocaleLowerCase('es').includes(text) || s.versionName.toLocaleLowerCase('es').includes(text));
}

export function metricRow(result: Pick<ComparisonResult, 'metrics'>, metric: ComparisonMetric): MetricRow | undefined {
  return result.metrics.find(row => row.metric === metric);
}

// ---------------------------------------------------------------- headline

export interface Headline {
  evaluationId: string;
  npv: number;
  /** The second highest NPV, if any, and how far behind it is. */
  runnerUpId: string | null;
  margin: number | null;
  relative: number | null;
  /** Another scenario has exactly the same NPV. */
  tied: boolean;
  /** Every NPV is negative: the best one only loses the least. */
  allNegative: boolean;
}

/** The scenario with the highest comparable NPV and its margin over the second one. No scenario is a reference. */
export function headline(result: Pick<ComparisonResult, 'metrics'>): Headline | null {
  const row = metricRow(result, 'NPV');
  if (!row) return null;
  const comparable = row.values.filter(cell => finite(cell.value.value) && cell.standing !== 'NOT_COMPARABLE')
    .sort((a, b) => b.value.value! - a.value.value!);
  if (!comparable.length) return null;
  const [best, second] = comparable;
  const margin = second ? best.value.value! - second.value.value! : null;
  return {
    evaluationId: best.evaluationId,
    npv: best.value.value!,
    runnerUpId: second?.evaluationId ?? null,
    margin,
    relative: margin == null || !second.value.value ? null : margin / Math.abs(second.value.value),
    tied: margin === 0,
    allNegative: comparable.every(cell => cell.value.value! < 0),
  };
}

/** Cells ordered best first (by rank); unranked ones keep their order at the end. */
export function byRank<T extends Pick<MetricCell, 'rank'>>(cells: T[]): T[] {
  return cells.map((cell, index) => ({ cell, index }))
    .sort((a, b) => (a.cell.rank ?? Infinity) - (b.cell.rank ?? Infinity) || a.index - b.index)
    .map(entry => entry.cell);
}

// ---------------------------------------------------------------- bars

export interface BarScale {
  /** Where zero sits, 0..1 from the left. */
  zero: number;
  /** Position of a value, 0..1 from the left. */
  position: (value: number) => number;
}

/** A shared horizontal scale for bars of one metric: always includes zero, so negatives grow left. */
export function barScale(values: (number | null | undefined)[]): BarScale | null {
  const present = values.filter(finite);
  if (!present.length) return null;
  const min = Math.min(0, ...present);
  const max = Math.max(0, ...present);
  const span = max - min || 1;
  return { zero: (0 - min) / span, position: value => (value - min) / span };
}

// ---------------------------------------------------------------- chart

export type SeriesField = 'cumulativeCashFlow' | 'cashFlow' | 'producedVolume' | 'exportedVolume';

export const SERIES_FIELDS: { field: SeriesField; label: string; unit: MetricUnit; question: string }[] = [
  { field: 'cumulativeCashFlow', label: 'Flujo de caja acumulado', unit: 'CURRENCY', question: '¿Cuándo se recupera la inversión?' },
  { field: 'cashFlow', label: 'Flujo de caja anual', unit: 'CURRENCY', question: '¿Cuánto deja cada año?' },
  { field: 'producedVolume', label: 'Volumen producido', unit: 'SIMULATOR_UNIT', question: '¿Cuánto se produce cada año?' },
  { field: 'exportedVolume', label: 'Volumen exportado', unit: 'SIMULATOR_UNIT', question: '¿Cuánto se exporta cada año?' },
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

// ---------------------------------------------------------------- selection

export interface StatusView { label: string; tone: Tone; detail: string | null }

/** How the picker describes a scenario's latest simulation. */
export function scenarioStatusView(scenario: Pick<ComparableScenario, 'status' | 'latestEvaluatedAt' | 'mode' | 'rounds' | 'job'>): StatusView {
  const when = scenario.latestEvaluatedAt
    ? new Date(scenario.latestEvaluatedAt).toLocaleDateString('es-AR', { day: 'numeric', month: 'short', year: 'numeric' })
    : null;
  const kind = scenario.mode === 'PROBABILISTIC' ? `${scenario.rounds ?? '?'} rondas` : scenario.mode === 'DETERMINISTIC' ? 'determinista' : null;
  const failed = scenario.job && (scenario.job.status === 'FAILED' || scenario.job.status === 'INTERRUPTED');
  switch (scenario.status) {
    case 'SIMULATING':
      return { label: 'Simulando…', tone: 'neutral',
        detail: scenario.job ? `${scenario.job.completedRounds} de ${scenario.job.rounds} rondas` : null };
    case 'UP_TO_DATE':
      return { label: 'Simulación al día', tone: 'good', detail: [when && `Simulado el ${when}`, kind].filter(Boolean).join(' · ') || null };
    case 'OUTDATED':
      return { label: 'Cambió desde la última simulación', tone: 'bad',
        detail: failed ? 'La última simulación falló. Volvé a intentarlo.' : when ? `Última simulación: ${when}` : null };
    default:
      return { label: 'Sin simular', tone: 'muted', detail: failed ? 'La última simulación falló. Volvé a intentarlo.' : null };
  }
}

export interface SelectionCheck {
  canCompare: boolean;
  /** Why "Comparar" is disabled, in plain words; null when it is enabled. */
  reason: string | null;
  /** Selected scenarios that must be simulated and can be from here. */
  toSimulate: ComparableScenario[];
  /** Selected scenarios that must be simulated but cannot be from here (incomplete configuration, no permission). */
  blocked: ComparableScenario[];
}

export function selectionCheck(selected: ComparableScenario[]): SelectionCheck {
  const pending = selected.filter(s => s.status !== 'UP_TO_DATE');
  const running = pending.filter(s => s.status === 'SIMULATING');
  const needing = pending.filter(s => s.status !== 'SIMULATING');
  const toSimulate = needing.filter(s => s.ready && s.canSimulate);
  const blocked = needing.filter(s => !s.ready || !s.canSimulate);
  let reason: string | null = null;
  if (selected.length < MIN_SCENARIOS) reason = `Elegí al menos ${MIN_SCENARIOS} escenarios para comparar.`;
  else if (selected.length > MAX_SCENARIOS) reason = `Se pueden comparar hasta ${MAX_SCENARIOS} escenarios a la vez.`;
  else if (running.length) reason = running.length === 1 ? 'Esperá a que termine la simulación en curso.' : `Esperá a que terminen ${running.length} simulaciones en curso.`;
  else if (needing.length) reason = needing.length === 1 ? 'Un escenario necesita simularse antes de comparar.' : `${needing.length} escenarios necesitan simularse antes de comparar.`;
  return { canCompare: reason === null, reason, toSimulate, blocked };
}

/** The request for the selected scenarios; their order only decides their colours. */
export function toRequest(scenarioIds: string[]): ComparisonRequest {
  return { items: scenarioIds.map(scenarioId => ({ scenarioId })) };
}

export interface SelectionRef { projectId: string; scenarioId: string }

/** `?escenarios=project:scenario,…` so "Elegir otros escenarios" reopens the picker with the same ones. */
export function selectionToQuery(selection: SelectionRef[]): string {
  return selection.map(s => `${s.projectId}:${s.scenarioId}`).join(',');
}

/** With `projectId`, scenarios of any other project are dropped: a comparison never crosses projects. */
export function selectionFromQuery(value: string | null, projectId?: string): SelectionRef[] {
  if (!value) return [];
  const seen = new Set<string>();
  const refs: SelectionRef[] = [];
  for (const part of value.split(',')) {
    const [ref, scenarioId] = part.trim().split(':');
    if (!ref || !scenarioId || seen.has(scenarioId) || (projectId && ref !== projectId)) continue;
    seen.add(scenarioId);
    refs.push({ projectId: ref, scenarioId });
  }
  return refs.slice(0, MAX_SCENARIOS);
}

export interface ResolvedSelection {
  refs: SelectionRef[];
  /** Entries in the old `project:version` format that were turned into their version's default scenario. */
  migrated: number;
  /** Entries that match no scenario of the project (deleted, or from another project) and were dropped. */
  discarded: number;
}

/**
 * Checks a selection against the project's scenarios. An id that is a scenario stays; an id that is a **version** (the format
 * used before scenarios existed) becomes that version's default scenario; anything else is dropped, and the caller says so.
 */
export function resolveSelection(refs: SelectionRef[], catalog: ComparableScenario[]): ResolvedSelection {
  const byScenario = new Map(catalog.map(s => [s.scenarioId, s]));
  const resolved: SelectionRef[] = [];
  const seen = new Set<string>();
  let migrated = 0;
  let discarded = 0;
  for (const ref of refs) {
    let scenario = byScenario.get(ref.scenarioId);
    let wasVersion = false;
    if (!scenario) {
      const ofVersion = catalog.filter(s => s.versionId === ref.scenarioId);
      scenario = ofVersion.find(s => s.defaultScenario) ?? ofVersion[0];
      wasVersion = !!scenario;
    }
    if (!scenario || seen.has(scenario.scenarioId)) {
      if (!scenario) discarded += 1;
      continue;
    }
    seen.add(scenario.scenarioId);
    if (wasVersion) migrated += 1;
    resolved.push({ projectId: scenario.projectId, scenarioId: scenario.scenarioId });
  }
  return { refs: resolved.slice(0, MAX_SCENARIOS), migrated, discarded };
}

/** The sentence that tells the person what happened to the selection they arrived with; null when nothing changed. */
export function selectionNotice(resolved: Pick<ResolvedSelection, 'migrated' | 'discarded'>): string | null {
  const parts: string[] = [];
  if (resolved.migrated) parts.push(`${resolved.migrated} ${resolved.migrated === 1 ? 'enlace apuntaba' : 'enlaces apuntaban'} a una versión: se eligió su escenario por defecto`);
  if (resolved.discarded) parts.push(`${resolved.discarded} ${resolved.discarded === 1 ? 'escenario ya no existe' : 'escenarios ya no existen'} y se quitó de la selección`);
  return parts.length ? parts.join('; ') + '.' : null;
}
