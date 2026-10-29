import { useEffect, useRef, useState } from 'react';
import type { Evaluation } from '../../types/economics';
import { operationalColumns, operationalNodes, operationalNumber, operationalDisplay, operationalTotal, operationalYear } from '../../utils/operationalResults';
import { downloadCsv, operationalCsv } from '../../utils/resultsCsv';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Alert } from '../ui/Alert';

export function OperationalResults({ evaluation, active = true, round }: { evaluation: Evaluation; active?: boolean; round?: number }) {
  const [hover, setHover] = useState<{ text: string; x: number; y: number } | null>(null);
  const [focus, setFocus] = useState<{ text: string; x: number; y: number } | null>(null);
  const detail = hover ?? focus;
  useEffect(() => {
    const dismiss = () => { setHover(null); setFocus(null); };
    window.addEventListener('scroll', dismiss, true);
    window.addEventListener('resize', dismiss);
    return () => { window.removeEventListener('scroll', dismiss, true); window.removeEventListener('resize', dismiss); };
  }, []);
  const chart = useRef<SVGSVGElement>(null);
  const nodes = operationalNodes(evaluation);
  const [selection, setSelection] = useState(nodes[0]?.id ?? '');
  const nodeId = nodes.some(n => n.id === selection) ? selection : nodes[0]?.id ?? '';
  const { rows, units } = operationalDisplay(evaluation, nodeId);
  const columns = operationalColumns.map(([field, label]) => [field, label, units[field]] as const);
  const sameUnit = units.output === units.exported;
  const years = evaluation.snapshot.configuration?.years;
  const max = Math.max(1, ...rows.flatMap(r => [r.output, r.exported]).filter((v): v is number => typeof v === 'number' && Number.isFinite(v)));
  const width = Math.max(640, rows.length * 80 + 70);
  useEffect(() => {
    if (!active) return;
    const preference = window.matchMedia('(prefers-reduced-motion: reduce)');
    const animations: Animation[] = [];
    if (!preference.matches) chart.current?.querySelectorAll('rect').forEach((bar, index) => {
      animations.push(bar.animate([{ transform: 'scaleY(0)' }, { transform: 'scaleY(1)' }], {
        duration: 550, delay: Math.min(Math.floor(index / 2) * 45, 350), easing: 'cubic-bezier(0.22, 1, 0.36, 1)', fill: 'backwards',
      }));
    });
    const stop = () => animations.forEach(animation => animation.cancel());
    preference.addEventListener('change', stop);
    return () => { stop(); preference.removeEventListener('change', stop); };
  }, [active, nodeId, evaluation]);
  if (!nodes.length) return <Card><h2 className="text-lg font-semibold">Sin datos operativos guardados</h2><p className="mt-2 text-sm text-ink-600">Esta evaluación no contiene métricas operativas por nodo. Sus resultados económicos siguen disponibles. No se reconstruyen datos a partir del escenario actual.</p></Card>;
  return <div className="space-y-5">
    {active && detail && <div role="tooltip" className="pointer-events-none fixed z-50 w-72 max-w-[calc(100vw-16px)] rounded-lg bg-ink-800 px-3 py-2 text-sm text-white shadow-lg"
      style={{ left: Math.max(8, Math.min(detail.x + 14, window.innerWidth - 296)), top: Math.max(8, Math.min(detail.y + 14, window.innerHeight - 96)) }}>{detail.text}</div>}
    <Card><div className="flex flex-wrap items-start justify-between gap-4"><div><h2 className="text-lg font-semibold">Operación del escenario</h2><p className="mt-1 text-sm text-ink-500">Consultá la evolución de cada nodo durante la simulación.</p></div>
      <label className="min-w-0 text-sm font-medium">Nodo de la evaluación<select className="mt-2 block w-full max-w-full rounded-lg border border-ink-300 bg-white p-3 text-ink-800 sm:w-72" value={nodeId} onChange={e => { setSelection(e.target.value); setHover(null); setFocus(null); }}>{nodes.map(n => <option key={n.id} value={n.id}>{n.name}</option>)}</select></label></div>
      <p className="mt-4 text-xs leading-relaxed text-ink-500">Volúmenes convertidos con los factores guardados en esta evaluación. Cuando no hay una conversión inequívoca se conserva la unidad del simulador (u. sim.). Se muestran por nodo para evitar contar varias veces el volumen que recorre la cadena. Cada año operativo representa 365 días.</p>
    </Card>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{columns.filter(([field]) => field !== 'input' && field !== 'events').map(([field, label, unit]) => {
      const total = operationalTotal(rows, field, years);
      return <Card key={field}><h3 className="text-sm font-medium text-ink-500">{label}</h3><p className="mt-3 text-xl font-semibold tabular-nums text-ink-800">{operationalNumber(total)}{' '}{total != null && <span className="ml-1 text-base font-medium">{unit}</span>}</p><p className="mt-2 text-xs text-ink-500">{total == null ? 'Serie incompleta o dato ausente' : 'Acumulado del nodo'}</p></Card>;
    })}</div>
    <Card><h2 className="text-lg font-semibold">Producción y exportación anual</h2><div className="mt-2 flex flex-wrap gap-4 text-xs text-ink-600"><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-sm bg-brand-800" />Producción / salida</span><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-sm bg-ink-400" />Volumen exportado</span><span>{sameUnit ? units.output : 'Salida: ' + units.output + ' · Exportado: ' + units.exported}</span></div>
      <div className="mt-4 overflow-x-auto"><svg ref={chart} width={width} height="240" className="min-w-full" role="group" aria-label="Producción y exportación anual del nodo seleccionado. Valores exactos en la tabla de detalle.">
        {(sameUnit ? [0, 0.5, 1] : []).map(f => <g key={f}><line x1="65" x2={width} y1={190 - f * 155} y2={190 - f * 155} className="stroke-ink-200" /><text x="58" y={194 - f * 155} textAnchor="end" className="fill-ink-500 text-xs">{new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 }).format(max * f)}</text></g>)}
        {rows.map((r, i) => { const x = 70 + (i + 0.5) * (width - 70) / rows.length; return <g key={r.period}>{(['output', 'exported'] as const).map((field, j) => {
          const value = r[field]; if (value == null || !Number.isFinite(value)) return null;
          const seriesMax = sameUnit ? max : Math.max(1, ...rows.map(row => row[field] ?? 0).filter(Number.isFinite));
          const h = Math.max(0, value) / seriesMax * 155;
          const text = operationalYear(evaluation, r.period) + ' · ' + (field === 'output' ? 'Producción / salida' : 'Volumen exportado') + ': ' + operationalNumber(value) + ' ' + units[field];
          return <rect key={field} tabIndex={0} role="img" aria-label={text}
            onPointerMove={event => setHover({ text, x: event.clientX, y: event.clientY })}
            onPointerLeave={() => setHover(null)}
            onFocus={event => { const bounds = event.currentTarget.getBoundingClientRect(); setFocus({ text, x: bounds.right, y: bounds.top }); }}
            onBlur={() => setFocus(null)}
            onClick={event => setHover({ text, x: event.clientX, y: event.clientY })}
            onKeyDown={event => { if (event.key === 'Escape') { setHover(null); setFocus(null); } }}
            x={x - 23 + j * 24} y={190 - h} width="20" height={Math.max(h, 1)} rx="3" className={'origin-[0_190px] ' + (j ? 'fill-ink-400' : 'fill-brand-800')} />;
        })}<text x={x} y="215" textAnchor="middle" className="fill-ink-600 text-xs">{evaluation.snapshot.configuration ? operationalYear(evaluation, r.period) : 'P' + r.period}</text></g>; })}
      </svg></div><p className="mt-2 text-xs text-ink-500">{!sameUnit && 'Las series usan escalas independientes porque sus unidades son distintas. '}Las exportaciones son una métrica independiente: no se suman a la producción. Los datos ausentes no se dibujan como cero.</p>
    </Card>
    {rows.some(r => operationalColumns.some(([f]) => r[f] == null)) && <Alert>Hay valores no disponibles en esta evaluación. Se conservan como datos ausentes.</Alert>}
    <Card className="relative"><div className="absolute right-6 top-6"><Button variant="secondary" disabled={!rows.length} onClick={() => downloadCsv('resultados-operativos-' + evaluation.id + (round ? '-ronda-' + round : '') + '-nodo-' + nodeId, operationalCsv(evaluation, nodeId, round))}>Descargar CSV</Button></div><details><summary className="min-h-10 cursor-pointer py-2 pr-36 font-semibold leading-6 text-ink-800">Detalle anual del nodo</summary><div className="mt-5 overflow-x-auto"><table className="w-full text-right text-sm"><thead><tr><th scope="col" className="whitespace-nowrap p-3 text-left">Año / período</th>{columns.map(([key, label, unit]) => <th scope="col" key={key} className="p-3">{label}<span className="block whitespace-nowrap text-xs font-normal text-ink-500">{unit}</span></th>)}</tr></thead><tbody>{rows.map(r => <tr key={r.period} className="border-t border-ink-100"><th scope="row" className="whitespace-nowrap p-3 text-left">{operationalYear(evaluation, r.period)} / {r.period}</th>{columns.map(([field]) => <td key={field} className="whitespace-nowrap p-3 tabular-nums">{operationalNumber(r[field])}</td>)}</tr>)}</tbody></table></div><p className="mt-3 text-xs text-ink-500">Horas y eventos corresponden al nodo seleccionado. El período 1 es el primer año de operación, posterior a la inversión.</p></details></Card>
  </div>;
}
