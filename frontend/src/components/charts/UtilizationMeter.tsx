import { formatPercent } from '../../utils/number';

/**
 * A ratio against its limit, as a meter: the fill is the share used and the
 * track is a lighter step of the same ramp, so the whole bar reads as one
 * measure. The percentage is always printed beside it.
 */
export function UtilizationMeter({ ratio }: { ratio: number | null }) {
  if (ratio == null) {
    return <span className="text-ink-500">—</span>;
  }
  const clamped = Math.min(1, Math.max(0, ratio));
  return (
    <span className="flex items-center gap-2" title={`Utilización: ${formatPercent(ratio, 1)}`}>
      <span className="relative h-2 w-16 overflow-hidden rounded-full bg-brand-100" aria-hidden="true">
        <span className="absolute inset-y-0 left-0 rounded-full bg-brand-600" style={{ width: `${clamped * 100}%` }} />
      </span>
      <span className="tabular-nums">{formatPercent(ratio)}</span>
    </span>
  );
}
