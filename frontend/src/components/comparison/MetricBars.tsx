import type { ComparisonMetric, ComparisonResult } from '../../types/comparison';
import {
  METRIC_COPY,
  TONE_CLASSES,
  barScale,
  byRank,
  compactMetric,
  formatMetric,
  gapText,
  metricRow,
  rankLabel,
  roundsNote,
  seriesColor,
  standingTone,
  statusText,
} from '../../utils/comparison';
import { Card } from '../ui/Card';
import { CheckIcon } from '../ui/icons';

interface MetricBarsProps {
  result: ComparisonResult;
  metric: ComparisonMetric;
  names: Record<string, string>;
  /** `lg` for the four headline indicators, `sm` for the denser second layer. */
  size?: 'lg' | 'sm';
}

/**
 * One indicator across all scenarios as a ranking: best first, aligned horizontal bars
 * on a shared scale that always includes zero (negatives grow left), each labelled with
 * its scenario, value, position and how far it is from the best. Colour follows the
 * scenario, never its rank; identity never rests on colour alone.
 */
export function MetricBars({ result, metric, names, size = 'lg' }: MetricBarsProps) {
  const row = metricRow(result, metric);
  if (!row) return null;
  const copy = METRIC_COPY[metric] ?? { title: row.label, meaning: '' };
  const index = new Map(result.scenarios.map((s, i) => [s.evaluationId, i]));
  const currencyOf = (id: string) => result.scenarios.find(s => s.evaluationId === id)?.currency;
  const scale = barScale(row.values.map(cell => cell.value.value));
  const large = size === 'lg';
  const ranked = row.values.some(cell => cell.rank != null);

  return <Card className="flex h-full flex-col gap-4">
    <div>
      <h3 className={`${large ? 'text-lg' : 'text-base'} font-semibold text-ink-800`}>{copy.title}</h3>
      {copy.meaning && <p className="mt-1 text-sm text-ink-500">{copy.meaning}</p>}
    </div>
    <ol className={`flex flex-col ${large ? 'gap-4' : 'gap-3'}`}>
      {byRank(row.values).map(cell => {
        const color = seriesColor(index.get(cell.evaluationId) ?? 0);
        const value = cell.value.value;
        const best = cell.standing === 'BEST';
        const gap = best || cell.standing === 'TIED' ? null : gapText(row, cell, currencyOf(cell.evaluationId));
        const note = cell.value.availableRounds < cell.value.totalRounds ? roundsNote(cell) : null;
        const position = value != null && scale ? scale.position(value) : null;
        const left = position == null || !scale ? 0 : Math.min(position, scale.zero);
        const width = position == null || !scale ? 0 : Math.abs(position - scale.zero);
        return <li key={cell.evaluationId} className="flex flex-col gap-1.5">
          <div className="flex items-baseline justify-between gap-3">
            <span className="flex min-w-0 items-center gap-2">
              {ranked && <span className="w-7 shrink-0 text-xs font-semibold text-ink-500 tabular-nums">{rankLabel(cell.rank) ?? '—'}</span>}
              <span aria-hidden="true" className={`h-2.5 w-2.5 shrink-0 rounded-full ${color.bg}`} />
              <span className="truncate text-sm text-ink-700" title={names[cell.evaluationId]}>{names[cell.evaluationId]}</span>
            </span>
            <span className="flex shrink-0 items-baseline gap-2">
              {best && <span className="inline-flex items-center gap-1 text-xs font-semibold text-brand-800">
                <CheckIcon className="h-3.5 w-3.5" /> Mejor</span>}
              <span className={`tabular-nums ${value == null ? 'text-sm italic text-ink-500' : large ? 'text-xl font-semibold text-ink-800' : 'text-base font-semibold text-ink-800'}`}
                title={value == null ? undefined : formatMetric(row.unit, value, currencyOf(cell.evaluationId))}>
                {value == null ? statusText(cell.value.status) : compactMetric(row.unit, value, currencyOf(cell.evaluationId))}
              </span>
            </span>
          </div>
          <div className={`relative ${large ? 'h-3' : 'h-2'} rounded-full bg-ink-100`} aria-hidden="true">
            {scale && scale.zero > 0 && scale.zero < 1 &&
              <span className="absolute -top-1 -bottom-1 w-px bg-ink-400" style={{ left: `${scale.zero * 100}%` }} />}
            {position != null && <span className={`absolute inset-y-0 rounded-[4px] ${color.bg}`}
              style={{ left: `${left * 100}%`, width: `max(${width * 100}%, 3px)` }} />}
          </div>
          {(gap || note) && <div className="flex flex-wrap items-center gap-2">
            {gap && <span className={`relative rounded-full border px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[standingTone(cell.standing)]}`}>{gap}</span>}
            {note && <span className="text-xs text-ink-500">{note}</span>}
          </div>}
        </li>;
      })}
    </ol>
  </Card>;
}
