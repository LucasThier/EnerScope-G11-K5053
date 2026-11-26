const formatter = new Intl.DateTimeFormat('es-AR', {
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
});

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? '—' : formatter.format(date);
}

const shortFormatter = new Intl.DateTimeFormat('es-AR', {
  day: 'numeric',
  month: 'short',
  year: 'numeric',
});

export function formatDateShort(iso: string | null | undefined): string | null {
  if (!iso) {
    return null;
  }
  const date = new Date(iso);
  return Number.isNaN(date.getTime()) ? null : shortFormatter.format(date).replace(/\./g, '').replace(/ de /g, ' ');
}

const MINUTE = 60_000;
const HOUR = 60 * MINUTE;
const DAY = 24 * HOUR;

/**
 * "hace 5 min", "hace 2 h", "ayer", "hace 3 días"; past a month it falls back to the short date. `now` is a
 * parameter so the result is testable. A date in the future (clock skew) reads as "recién".
 */
export function formatRelative(iso: string | null | undefined, now: number = Date.now()): string | null {
  if (!iso) {
    return null;
  }
  const time = new Date(iso).getTime();
  if (Number.isNaN(time)) {
    return null;
  }
  const diff = now - time;
  if (diff < MINUTE) {
    return 'recién';
  }
  if (diff < HOUR) {
    return `hace ${Math.floor(diff / MINUTE)} min`;
  }
  if (diff < DAY) {
    return `hace ${Math.floor(diff / HOUR)} h`;
  }
  const days = Math.floor(diff / DAY);
  if (days === 1) {
    return 'ayer';
  }
  if (days <= 30) {
    return `hace ${days} días`;
  }
  return formatDateShort(iso);
}
