import { useState } from 'react';
import type { ComparisonResult } from '../../types/comparison';
import { SERIES_FIELDS, chartScale, formatMetric, lineSegments, type SeriesField } from '../../utils/comparison';
import { SelectField } from '../ui/SelectField';

/** One stroke colour and dash per scenario, so lines stay distinguishable without colour. */
const STROKES = ['stroke-brand-800', 'stroke-warn-700', 'stroke-danger-600', 'stroke-ink-500', 'stroke-brand-400', 'stroke-ink-800'];
const FILLS = ['fill-brand-800', 'fill-warn-700', 'fill-danger-600', 'fill-ink-500', 'fill-brand-400', 'fill-ink-800'];
const DASHES = [undefined, '7 4', '2 4', '10 4 2 4'];

const WIDTH = 720;
const HEIGHT = 280;
const LEFT = 72;
const RIGHT = 16;
const TOP = 16;
const BOTTOM = 32;

// 6 colours × 4 dash patterns: every combination is unique up to 12 scenarios (the API allows 10).
function seriesStyle(index: number) {
  return { stroke: STROKES[index % STROKES.length], fill: FILLS[index % FILLS.length], dash: DASHES[index % DASHES.length] };
}

/** Year-by-year comparison of one series (cash flow or volumes) across scenarios. */
export function ComparisonChart({ result, names }: { result: ComparisonResult; names: Record<string, string> }) {
  const [field, setField] = useState<SeriesField>('cumulativeCashFlow');
  const definition = SERIES_FIELDS.find(f => f.field === field)!;
  const currency = result.scenarios[0]?.currency;
  const scale = chartScale(result.annual.map(s => s.points), field);
  const compact = new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 });

  const x = (year: number) => !scale || scale.maxYear === scale.minYear
    ? LEFT + (WIDTH - LEFT - RIGHT) / 2
    : LEFT + (year - scale.minYear) / (scale.maxYear - scale.minYear) * (WIDTH - LEFT - RIGHT);
  const y = (value: number) => !scale ? 0 : TOP + (scale.max - value) / (scale.max - scale.min) * (HEIGHT - TOP - BOTTOM);
  const years = scale ? Array.from({ length: scale.maxYear - scale.minYear + 1 }, (_, i) => scale.minYear + i) : [];
  const labelEvery = Math.max(1, Math.ceil(years.length / 12));

  return <div className="space-y-4">
    <div className="max-w-xs">
      <SelectField label="Serie" density="compact" value={field} onChange={event => setField(event.target.value as SeriesField)}>
        {SERIES_FIELDS.map(f => <option key={f.field} value={f.field}>{f.label}</option>)}
      </SelectField>
    </div>
    {!scale ? <p className="text-sm text-ink-500">Las evaluaciones no guardan datos para esta serie.</p> : <>
      <svg viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="h-auto w-full" role="img"
        aria-label={`${definition.label} por año para cada escenario. Los valores exactos están en la tabla siguiente.`}>
        {scale.ticks.map(tick => <g key={tick}>
          <line x1={LEFT} x2={WIDTH - RIGHT} y1={y(tick)} y2={y(tick)} className={tick === 0 ? 'stroke-ink-300' : 'stroke-ink-100'}
            strokeDasharray={tick === 0 ? undefined : '4 4'} />
          <text x={LEFT - 8} y={y(tick) + 4} textAnchor="end" className="fill-ink-500 text-[11px]">{compact.format(tick)}</text>
        </g>)}
        {years.map((year, i) => i % labelEvery === 0 &&
          <text key={year} x={x(year)} y={HEIGHT - 10} textAnchor="middle" className="fill-ink-600 text-[11px]">{year}</text>)}
        {result.annual.map((series, index) => {
          const style = seriesStyle(index);
          return <g key={series.evaluationId}>
            {lineSegments(series.points, field).map((segment, s) => segment.length === 1
              ? <circle key={s} cx={x(segment[0].year)} cy={y(segment[0].value)} r={3.5} className={style.fill} />
              : <polyline key={s} fill="none" strokeWidth={2.5} className={style.stroke} strokeDasharray={style.dash}
                  points={segment.map(p => `${x(p.year)},${y(p.value)}`).join(' ')} />)}
          </g>;
        })}
      </svg>
      <ul className="flex flex-wrap gap-4 text-sm text-ink-700" aria-label="Referencias">
        {result.annual.map((series, index) => {
          const style = seriesStyle(index);
          return <li key={series.evaluationId} className="flex items-center gap-2">
            <svg width="28" height="10" aria-hidden="true"><line x1="0" x2="28" y1="5" y2="5" strokeWidth={2.5} className={style.stroke} strokeDasharray={style.dash} /></svg>
            {names[series.evaluationId]}
          </li>;
        })}
      </ul>
      <details className="text-sm">
        <summary className="cursor-pointer text-brand-800">Ver valores por año</summary>
        <div className="mt-2 overflow-x-auto">
          <table className="w-full text-left">
            <thead><tr className="border-b border-ink-200"><th className="p-2">Año</th>
              {result.annual.map(s => <th key={s.evaluationId} className="p-2">{names[s.evaluationId]}</th>)}</tr></thead>
            <tbody>{years.map(year => <tr key={year} className="border-b border-ink-100"><td className="p-2">{year}</td>
              {result.annual.map(s => {
                const point = s.points.find(p => p.year === year);
                return <td key={s.evaluationId} className="p-2 tabular-nums">{formatMetric(definition.unit, point?.[field], currency)}</td>;
              })}</tr>)}</tbody>
          </table>
        </div>
      </details>
    </>}
  </div>;
}
