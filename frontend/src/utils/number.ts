/**
 * Number formatting for the simulation and cost screens, in the app's es-AR
 * locale (dot thousands, comma decimals). Like `date.ts`, every helper answers
 * an em dash for a missing value so a cell never reads "NaN".
 */

const DASH = '—';

function valid(value: number | null | undefined): value is number {
  return value != null && Number.isFinite(value);
}

/** A plain number with up to `maxDecimals` decimals. */
export function formatNumber(value: number | null | undefined, maxDecimals = 0): string {
  if (!valid(value)) return DASH;
  return new Intl.NumberFormat('es-AR', { maximumFractionDigits: maxDecimals }).format(value);
}

/** Compact for large figures: 1,3 M · 12,9 mil. Small values keep two decimals. */
export function formatCompact(value: number | null | undefined): string {
  if (!valid(value)) return DASH;
  if (Math.abs(value) < 1000) return formatNumber(value, 2);
  return new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 }).format(value);
}

/** Money without a currency symbol: the model's costs carry no currency of their own. */
export function formatMoney(value: number | null | undefined, compact = false): string {
  if (!valid(value)) return DASH;
  if (compact) return `$ ${formatCompact(value)}`;
  return `$ ${new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value)}`;
}

/** A ratio (0.42) as a percentage (42 %). */
export function formatPercent(ratio: number | null | undefined, decimals = 0): string {
  if (!valid(ratio)) return DASH;
  return `${new Intl.NumberFormat('es-AR', { maximumFractionDigits: decimals }).format(ratio * 100)} %`;
}

/** Relative change of `value` against `base`, or null when it cannot be computed. */
export function relativeChange(value: number | null | undefined, base: number | null | undefined): number | null {
  if (!valid(value) || !valid(base) || base === 0) return null;
  return (value - base) / Math.abs(base);
}

/** A date and time, es-AR, for run timestamps. */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return DASH;
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return DASH;
  return new Intl.DateTimeFormat('es-AR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}
