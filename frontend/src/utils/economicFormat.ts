import type { Indicators, Payback } from '../types/economics';

export function money(value: number | null | undefined, currency?: string, whole = false): string {
  if (value == null || !Number.isFinite(value)) return 'No disponible';
  if (whole) value = Math.ceil(value);
  const digits = whole ? { minimumFractionDigits: 0, maximumFractionDigits: 0 } : {};
  if (!currency) return new Intl.NumberFormat('es-AR', { minimumFractionDigits: 2, maximumFractionDigits: 2, ...digits }).format(value) + ' (moneda no disponible)';
  try {
    return new Intl.NumberFormat('es-AR', { style: 'currency', currency, currencyDisplay: 'code', ...digits }).format(value);
  } catch {
    return 'Moneda no disponible';
  }
}
export const percent = (value: number | null | undefined) => value == null ? 'No disponible' : new Intl.NumberFormat('es-AR', { style: 'percent', maximumFractionDigits: 4 }).format(value);
export const dateTime = (value: string) => new Date(value).toLocaleString('es-AR');
export function irrText(irr: Indicators['irr'] | null | undefined) {
  if (!irr) return 'Datos insuficientes';
  const labels = { NO_SIGN_CHANGE: 'Sin cambio de signo en los flujos', NON_CONVENTIONAL: 'No calculable para este patrón de flujos',
    NUMERICAL_FAILURE: 'No se pudo resolver numéricamente', INSUFFICIENT_DATA: 'Datos insuficientes' };
  return irr.status === 'CALCULATED' ? irr.rate == null || !Number.isFinite(irr.rate) ? 'No disponible' : new Intl.NumberFormat('es-AR', { style: 'percent', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(irr.rate) : labels[irr.status] ?? 'No disponible';
}
export function paybackText(p: Payback | undefined) {
  if (!p) return 'Datos insuficientes';
  const labels = { NOT_RECOVERED_WITHIN_HORIZON: 'No recuperado dentro del horizonte', NOT_APPLICABLE: 'No aplica: sin inversión inicial neta',
    INSUFFICIENT_DATA: 'Datos insuficientes', NUMERICAL_FAILURE: 'No se pudo resolver numéricamente' };
  return p.status === 'RECOVERED' ? `Período ${p.period} · Año ${p.year}` : labels[p.status] ?? 'No disponible';
}
