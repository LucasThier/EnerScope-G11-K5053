import type { ReactNode } from 'react';

export interface BarItem {
  key: string;
  label: string;
  /** Null renders an empty row with an em dash: "no value", not zero. */
  value: number | null;
  /** Small text under the label, e.g. "base". */
  note?: string;
}

interface BarListProps {
  title: string;
  subtitle?: string;
  items: BarItem[];
  format: (value: number | null) => string;
  /** Extra content under each value, e.g. a delta against the baseline. */
  annotate?: (item: BarItem) => ReactNode;
}

/**
 * One series of horizontal bars, one row per item — the form for comparing a
 * magnitude across a handful of named things (scenarios, verticals). Built to
 * the dataviz specs: a single hue (`brand-600`, validated at ≥ 3:1 on white),
 * bars ≤ 24px thick growing from one baseline with a 4px rounded data end, the
 * value set outside the bar's tip so it is never clipped, and identity carried
 * by the row label in text tokens — never by colour. Each row is focusable and
 * shows the exact value in a tooltip on hover and focus; every value is also
 * printed, so the tooltip never gates anything.
 */
export function BarList({ title, subtitle, items, format, annotate }: BarListProps) {
  const max = Math.max(0, ...items.map((item) => item.value ?? 0));

  return (
    <figure className="flex flex-col gap-3">
      <figcaption>
        <span className="block text-sm font-semibold text-ink-800">{title}</span>
        {subtitle && <span className="block text-xs text-ink-500">{subtitle}</span>}
      </figcaption>
      <ul className="flex flex-col gap-2">
        {items.map((item) => {
          const width = item.value != null && max > 0 ? Math.max(0, (item.value / max) * 100) : 0;
          const valueText = format(item.value);
          return (
            <li
              key={item.key}
              tabIndex={0}
              aria-label={`${item.label}: ${valueText}`}
              className="group relative grid grid-cols-[minmax(0,9rem)_1fr_auto] items-center gap-3 rounded-md focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
            >
              <span className="min-w-0">
                <span className="block truncate text-sm text-ink-700" title={item.label}>
                  {item.label}
                </span>
                {item.note && <span className="block text-xs text-ink-500">{item.note}</span>}
              </span>
              <span className="flex h-6 items-center border-l border-ink-200">
                {width > 0 && (
                  <span
                    className="h-4 rounded-r bg-brand-600 transition-colors group-hover:bg-brand-700 group-focus-visible:bg-brand-700"
                    style={{ width: `max(${width}%, 2px)` }}
                  />
                )}
              </span>
              <span className="min-w-[6.5rem] text-right">
                <span className="block text-sm font-semibold tabular-nums text-ink-800">{valueText}</span>
                {annotate && <span className="block text-xs">{annotate(item)}</span>}
              </span>
              <span
                role="presentation"
                className="pointer-events-none absolute -top-8 left-36 z-10 hidden whitespace-nowrap rounded-md bg-ink-800 px-2 py-1 text-xs text-white shadow-md group-hover:block group-focus-visible:block"
              >
                <strong className="font-semibold">{valueText}</strong> · {item.label}
              </span>
            </li>
          );
        })}
      </ul>
    </figure>
  );
}
