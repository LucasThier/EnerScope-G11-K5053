import { useRef, useState, type KeyboardEvent, type PointerEvent } from 'react';
import type { ComparisonResult } from '../../types/comparison';
import { SERIES_FIELDS, chartScale, compactMetric, formatMetric, lineSegments, seriesColor, type SeriesField } from '../../utils/comparison';
import { Card } from '../ui/Card';

const WIDTH = 760;
const HEIGHT = 300;
const LEFT = 64;
const RIGHT = 20;
const TOP = 16;
const BOTTOM = 34;

/**
 * Year-by-year comparison of one series across scenarios: one 2px line per scenario,
 * a zero baseline, a crosshair with every scenario's value on hover or arrow keys, and
 * the exact values in a table underneath. Missing years break the line.
 */
export function ComparisonChart({ result, names }: { result: ComparisonResult; names: Record<string, string> }) {
  const [field, setField] = useState<SeriesField>('cumulativeCashFlow');
  const [year, setYear] = useState<number | null>(null);
  const svg = useRef<SVGSVGElement>(null);
  const definition = SERIES_FIELDS.find(f => f.field === field)!;
  const currency = result.scenarios[0]?.currency;
  const scale = chartScale(result.annual.map(s => s.points), field);

  const x = (value: number) => !scale || scale.maxYear === scale.minYear
    ? LEFT + (WIDTH - LEFT - RIGHT) / 2
    : LEFT + (value - scale.minYear) / (scale.maxYear - scale.minYear) * (WIDTH - LEFT - RIGHT);
  const y = (value: number) => !scale ? 0 : TOP + (scale.max - value) / (scale.max - scale.min) * (HEIGHT - TOP - BOTTOM);
  const years = scale ? Array.from({ length: scale.maxYear - scale.minYear + 1 }, (_, i) => scale.minYear + i) : [];
  const labelEvery = Math.max(1, Math.ceil(years.length / 12));
  const valueAt = (seriesIndex: number, at: number) => result.annual[seriesIndex].points.find(p => p.year === at)?.[field] ?? null;

  function pointerMove(event: PointerEvent<SVGSVGElement>) {
    if (!scale || !svg.current) return;
    const box = svg.current.getBoundingClientRect();
    const ratio = ((event.clientX - box.left) / box.width * WIDTH - LEFT) / (WIDTH - LEFT - RIGHT);
    const nearest = Math.round(scale.minYear + ratio * (scale.maxYear - scale.minYear));
    setYear(Math.min(scale.maxYear, Math.max(scale.minYear, nearest)));
  }

  function keyDown(event: KeyboardEvent<SVGSVGElement>) {
    if (!scale) return;
    if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
      event.preventDefault();
      const step = event.key === 'ArrowRight' ? 1 : -1;
      setYear(current => Math.min(scale.maxYear, Math.max(scale.minYear, (current ?? (step > 0 ? scale.minYear - 1 : scale.maxYear + 1)) + step)));
    } else if (event.key === 'Escape') setYear(null);
  }

  const tooltipLeft = year == null ? 0 : x(year) / WIDTH * 100;

  return <Card className="flex flex-col gap-4">
    <div className="flex flex-wrap items-end justify-between gap-4">
      <div>
        <h2 className="text-lg font-semibold text-ink-800">{definition.question}</h2>
        <p className="mt-1 text-sm text-ink-500">{definition.label}, año por año. Pasá el mouse sobre el gráfico para ver los valores.</p>
      </div>
      <div role="group" aria-label="Serie del gráfico" className="flex flex-wrap gap-1 rounded-control bg-ink-50 p-1">
        {SERIES_FIELDS.map(f => <button key={f.field} type="button" onClick={() => setField(f.field)} aria-pressed={field === f.field}
          className={`rounded-control px-3 py-1.5 text-sm font-medium focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ${
            field === f.field ? 'bg-surface text-ink-800 shadow-raise' : 'text-ink-600 hover:text-ink-800'}`}>{f.label}</button>)}
      </div>
    </div>

    {!scale ? <p className="text-sm text-ink-500">Las simulaciones no guardan datos para esta serie.</p> : <>
      <ul className="flex flex-wrap gap-x-5 gap-y-2 text-sm text-ink-700" aria-label="Referencias">
        {result.annual.map((series, index) => <li key={series.evaluationId} className="flex items-center gap-2">
          <span aria-hidden="true" className={`h-0.5 w-6 rounded-full ${seriesColor(index).bg}`} />
          {names[series.evaluationId]}
        </li>)}
      </ul>
      <div className="relative">
        <svg ref={svg} viewBox={`0 0 ${WIDTH} ${HEIGHT}`} className="h-auto w-full touch-none focus:outline-none focus-visible:ring-2 focus-visible:ring-focus"
          role="img" tabIndex={0} aria-label={`${definition.label} por año para cada escenario. Usá las flechas para recorrer los años; los valores exactos están en la tabla.`}
          onPointerMove={pointerMove} onPointerLeave={() => setYear(null)} onKeyDown={keyDown} onBlur={() => setYear(null)}>
          {scale.ticks.map(tick => <g key={tick}>
            <line x1={LEFT} x2={WIDTH - RIGHT} y1={y(tick)} y2={y(tick)} className={tick === 0 ? 'stroke-ink-400' : 'stroke-ink-100'}
              strokeWidth={tick === 0 ? 1.5 : 1} />
            <text x={LEFT - 10} y={y(tick) + 4} textAnchor="end" className="fill-ink-500 text-[12px]">
              {compactMetric(definition.unit, tick, null)}</text>
          </g>)}
          {years.map((at, i) => i % labelEvery === 0 &&
            <text key={at} x={x(at)} y={HEIGHT - 10} textAnchor="middle" className="fill-ink-500 text-[12px]">{at}</text>)}
          {year != null && <line x1={x(year)} x2={x(year)} y1={TOP} y2={HEIGHT - BOTTOM} className="stroke-ink-300" strokeWidth={1} />}
          {result.annual.map((series, index) => {
            const color = seriesColor(index);
            return <g key={series.evaluationId}>
              {lineSegments(series.points, field).map((segment, s) => segment.length === 1
                ? <circle key={s} cx={x(segment[0].year)} cy={y(segment[0].value)} r={4} className={color.fill} />
                : <polyline key={s} fill="none" strokeWidth={2} strokeLinejoin="round" strokeLinecap="round" className={color.stroke}
                    points={segment.map(p => `${x(p.year)},${y(p.value)}`).join(' ')} />)}
              {year != null && valueAt(index, year) != null &&
                <circle cx={x(year)} cy={y(valueAt(index, year)!)} r={5} strokeWidth={2} className={`${color.fill} stroke-surface`} />}
            </g>;
          })}
        </svg>
        {year != null && <div role="status" className="pointer-events-none absolute top-2 z-10 w-60 rounded-control border border-ink-100 bg-raised px-3 py-2 text-sm shadow-float"
          style={{ left: `${tooltipLeft}%`, transform: tooltipLeft > 60 ? 'translateX(calc(-100% - 12px))' : 'translateX(12px)' }}>
          <p className="mb-1 font-semibold text-ink-800">Año {year}</p>
          <ul className="flex flex-col gap-1">
            {result.annual.map((series, index) => <li key={series.evaluationId} className="flex items-center justify-between gap-3">
              <span className="flex min-w-0 items-center gap-2">
                <span aria-hidden="true" className={`h-2 w-2 shrink-0 rounded-full ${seriesColor(index).bg}`} />
                <span className="truncate text-ink-600">{names[series.evaluationId]}</span>
              </span>
              <span className="shrink-0 font-medium text-ink-800 tabular-nums">{compactMetric(definition.unit, valueAt(index, year), currency)}</span>
            </li>)}
          </ul>
        </div>}
      </div>
      <details className="text-sm">
        <summary className="cursor-pointer font-medium text-brand-800">Ver la tabla de valores</summary>
        <div className="mt-3 overflow-x-auto">
          <table className="w-full text-left">
            <thead><tr className="border-b border-ink-200"><th scope="col" className="p-2 font-semibold text-ink-700">Año</th>
              {result.annual.map(s => <th key={s.evaluationId} scope="col" className="p-2 font-semibold text-ink-700">{names[s.evaluationId]}</th>)}</tr></thead>
            <tbody>{years.map(at => <tr key={at} className="border-b border-ink-100"><td className="p-2">{at}</td>
              {result.annual.map((s, index) => <td key={s.evaluationId} className="p-2 tabular-nums">
                {formatMetric(definition.unit, valueAt(index, at), currency)}</td>)}</tr>)}</tbody>
          </table>
        </div>
      </details>
    </>}
  </Card>;
}
