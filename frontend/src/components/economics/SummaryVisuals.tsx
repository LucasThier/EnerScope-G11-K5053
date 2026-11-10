import { useEffect, useId, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import type { NumericStatistics } from '../../types/economics';
import { bandSegments, finite, noDispersion, plotScale } from '../../utils/summaryVisuals';
import type { BandPoint } from '../../utils/summaryVisuals';
import { Card } from '../ui/Card';

type Format = (value: number | null) => string;
export function SummaryCard({ title, value, caption }: { title: string; value: string; caption: string }) {
  return <Card className="min-w-0"><h2 className="text-sm font-medium text-ink-600">{title}</h2><p className="my-3 break-words text-2xl font-semibold tabular-nums">{value}</p><p className="text-sm text-ink-500">{caption}</p></Card>;
}
export function StatisticalDetail({ children }: { children: ReactNode }) {
  return <details className="mt-5 border-t border-ink-100 pt-3"><summary className="cursor-pointer rounded p-2 text-sm font-medium focus-visible:outline-2 focus-visible:outline-brand-800">Ver detalle estadístico</summary>{children}</details>;
}
export function PercentileRange({ title, values, format }: { title: string; values: NumericStatistics; format: Format }) {
  const valid = values.count > 0 && [values.p10, values.p50, values.p90].every(finite);
  const scale = valid ? plotScale([values.p10, values.p50, values.p90]) : null;
  return <div className="min-w-0 space-y-2"><h3 className="font-semibold">{title}</h3>{scale ? <>
    <svg viewBox="0 0 500 50" className="h-14 w-full" role="img" aria-label={`${title}: P10 ${format(values.p10)}, P50 ${format(values.p50)}, P90 ${format(values.p90)}`}>
      <line x1="0" x2="500" y1="25" y2="25" className="stroke-ink-200" />
      {values.p10! <= 0 && values.p90! >= 0 && <g><line x1={500 * scale.position(0)} x2={500 * scale.position(0)} y1="4" y2="45" strokeDasharray="3 3" className="stroke-ink-500" /><text x={500 * scale.position(0)} y="12" textAnchor="middle" className="fill-ink-600 text-xs">0</text></g>}
      <line x1={500 * scale.position(values.p10!)} x2={500 * scale.position(values.p90!)} y1="25" y2="25" strokeWidth="8" strokeLinecap="round" className="stroke-brand-800/30" />
      <path d={`M ${500 * scale.position(values.p50!)} 17 l 8 8 l -8 8 l -8 -8 Z`} className="fill-brand-800" />
    </svg>
    <div className="grid grid-cols-3 gap-2 text-sm">{(['p10','p50','p90'] as const).map(k => <p className="break-words" key={k}><span className="block text-ink-500">{k.toUpperCase()}{k === 'p50' ? ' · Mediana' : ''}</span><strong className="font-medium tabular-nums">{format(values[k])}</strong></p>)}</div>
    {values.p10 === values.p90 && <p className="text-sm text-ink-500">Sin dispersión observada</p>}
  </> : <p className="text-sm text-ink-500">No disponible</p>}</div>;
}
export function BandChart({ title, points, format, unit }: { title: string; points: BandPoint[]; format: Format; unit: string }) {
  const id = useId();
  const container = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(640);
  useEffect(() => {
    const element = container.current;
    if (!element) return;
    const observer = new ResizeObserver(([entry]) => setWidth(Math.max(240, entry.contentRect.width)));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const [active, setActive] = useState<number | null>(null);
  const ordered = [...points].sort((a,b) => a.period - b.period);
  const scale = plotScale(ordered.filter(p => p.values.count > 0).flatMap(p => [p.values.p10, p.values.p50, p.values.p90]), true);
  const visible = ordered.filter(p => p.values.count > 0).flatMap(p => [p.values.p10,p.values.p50,p.values.p90]).filter(finite);
  const first = ordered[0]?.period ?? 0, last = ordered[ordered.length - 1]?.period ?? first;
  const x = (period: number) => last === first ? width / 2 : 50 + (width - 75) * (period - first) / (last - first);
  const y = (value: number) => 210 - 180 * scale!.position(value);
  const detail = active === null ? null : ordered.find(p => p.period === active);
  const describe = (p: BandPoint) => `${p.label}: P10 ${format(p.values.p10)}, P50 ${format(p.values.p50)}, P90 ${format(p.values.p90)}. Muestra: ${p.values.count}.`;
  return <div ref={container} className="mt-4">
    <p className="text-sm text-ink-500">{unit} · Línea y puntos: P50 · Banda: P10–P90</p>
    {visible.length > 0 && <p className="mt-1 text-sm text-ink-500">Rango de la banda: {format(Math.min(...visible))} a {format(Math.max(...visible))}</p>}
    {!scale ? <p className="py-8 text-center text-ink-500">No hay datos disponibles para este gráfico.</p> : <>
      <div><svg viewBox={`0 0 ${width} 260`} className="h-64 w-full" aria-labelledby={id} role="group">
        <title id={id}>{title}. Cada punto se puede consultar con el teclado o el puntero.</title>
        <line x1="50" x2={width - 25} y1={y(0)} y2={y(0)} strokeDasharray="4 4" className="stroke-ink-500" /><text x="41" y={y(0) + 4} textAnchor="end" className="fill-ink-600 text-xs">0</text>
        {bandSegments(ordered, ['p10','p90']).map((segment,i) => <path key={'band'+i} d={segment.map((p,j) => `${j ? 'L' : 'M'}${x(p.period)},${y(p.values.p90!)}`).join(' ') + ' ' + [...segment].reverse().map(p => `L${x(p.period)},${y(p.values.p10!)}`).join(' ') + ' Z'} className="fill-brand-800/10 stroke-brand-800/40" />)}
        {bandSegments(ordered, ['p50']).map((segment,i) => <path key={'line'+i} d={segment.map((p,j) => `${j ? 'L' : 'M'}${x(p.period)},${y(p.values.p50!)}`).join(' ')} fill="none" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" className="stroke-brand-800" />)}
        {ordered.map((p,i) => <g key={p.period}>
          {(i === 0 || i === ordered.length - 1 || i % Math.max(1, Math.ceil(ordered.length / (width < 500 ? 3 : 6))) === 0) && <text x={x(p.period)} y="240" textAnchor="middle" className="fill-ink-600 text-xs">{p.label}</text>}
          {p.values.count > 0 && finite(p.values.p50) && <g role="button" tabIndex={0} aria-label={describe(p)} aria-describedby={id+'-detail'} className="cursor-pointer focus-visible:outline-2 focus-visible:outline-brand-800" onFocus={() => setActive(p.period)} onBlur={() => setActive(null)} onMouseEnter={() => setActive(p.period)} onMouseLeave={() => setActive(null)} onClick={() => setActive(p.period)} onKeyDown={e => { if (e.key === 'Escape') setActive(null); if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setActive(p.period); } }}>
            <circle cx={x(p.period)} cy={y(p.values.p50)} r="12" fill="transparent" /><circle cx={x(p.period)} cy={y(p.values.p50)} r={active === p.period ? 6 : 4} strokeWidth="2" className="fill-brand-800 stroke-surface" />
          </g>}
        </g>)}
      </svg></div>
      <p id={id+'-detail'} role="status" className="min-h-12 rounded-control border border-ink-100 bg-ink-50 p-3 text-sm text-ink-600">{detail ? describe(detail) : 'Seleccioná un punto para consultar sus valores. Tab para recorrerlos; Escape para cerrar la ayuda.'}</p>
      {noDispersion(ordered) && <p className="mt-2 text-sm text-ink-500">Sin dispersión observada</p>}
      {ordered.some(p => p.values.count === 0 || ![p.values.p10,p.values.p50,p.values.p90].every(finite)) && <p className="mt-2 text-sm text-ink-500">Los datos ausentes se muestran como huecos.</p>}
    </>}
  </div>;
}
