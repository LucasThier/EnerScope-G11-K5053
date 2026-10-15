import type { ReactNode } from 'react';

interface StatTileProps {
  label: string;
  value: string;
  /** Secondary line: a range, a unit or a comparison. */
  detail?: ReactNode;
  /** The one figure a view leads with: larger type. */
  hero?: boolean;
}

/** A headline number. Proportional figures on purpose: tabular digits look loose at this size. */
export function StatTile({ label, value, detail, hero = false }: StatTileProps) {
  return (
    <div className="rounded-xl border border-ink-100 bg-white px-4 py-3">
      <div className="text-xs font-medium text-ink-500">{label}</div>
      <div className={`mt-1 font-semibold text-ink-800 ${hero ? 'text-4xl' : 'text-xl'}`}>{value}</div>
      {detail && <div className="mt-1 text-xs text-ink-500">{detail}</div>}
    </div>
  );
}
