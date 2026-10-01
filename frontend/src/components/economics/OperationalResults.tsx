import { useState } from 'react';
import type { Evaluation } from '../../types/economics';
import { operationalColumns, operationalNodes, operationalNumber, operationalRows, operationalTotal, operationalYear } from '../../utils/operationalResults';
import { Card } from '../ui/Card';
import { Alert } from '../ui/Alert';

export function OperationalResults({ evaluation }: { evaluation: Evaluation }) {
  const nodes = operationalNodes(evaluation);
  const [selection, setSelection] = useState(nodes[0]?.id ?? '');
  const nodeId = nodes.some(n => n.id === selection) ? selection : nodes[0]?.id ?? '';
  const rows = operationalRows(evaluation, nodeId);
  const years = evaluation.snapshot.configuration?.years;
  const max = Math.max(1, ...rows.flatMap(r => [r.output, r.exported]).filter((v): v is number => typeof v === 'number' && Number.isFinite(v)));
  const width = Math.max(640, rows.length * 80 + 70);
  if (!nodes.length) return <Card><h2 className="text-lg font-semibold">Sin datos operativos guardados</h2><p className="mt-2 text-sm text-ink-600">Esta evaluación no contiene métricas operativas por nodo. Sus resultados económicos siguen disponibles. No se reconstruyen datos a partir del escenario actual.</p></Card>;
  return <div className="space-y-5">
    <Card><div className="flex flex-wrap items-end justify-between gap-4"><div><h2 className="text-lg font-semibold">Operación del escenario</h2><p className="mt-1 text-sm text-ink-500">Consultá la evolución de cada nodo durante la simulación.</p></div>
      <label className="min-w-0 text-sm font-medium">Nodo de la evaluación<select className="mt-2 block w-full max-w-full rounded-lg border border-ink-300 bg-white p-3 text-ink-800 sm:w-72" value={nodeId} onChange={e => setSelection(e.target.value)}>{nodes.map(n => <option key={n.id} value={n.id}>{n.name}</option>)}</select></label></div>
      <p className="mt-4 text-xs leading-relaxed text-ink-500">Volúmenes en unidades del simulador (u. sim.), sin conversión comercial. Se muestran por nodo para evitar contar varias veces el volumen que recorre la cadena. Cada año operativo representa 365 días.</p>
    </Card>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{operationalColumns.filter(([field]) => field !== 'input' && field !== 'events').map(([field, label, unit]) => {
      const total = operationalTotal(rows, field, years);
      return <Card key={field}><h3 className="text-sm font-medium text-ink-500">{label}</h3><p className="mt-3 text-xl font-semibold tabular-nums text-ink-800">{operationalNumber(total)}</p><p className="mt-2 text-xs text-ink-500">{total == null ? 'Serie incompleta o dato ausente' : unit + ' · acumulado del nodo'}</p></Card>;
    })}</div>
    <Card><h2 className="text-lg font-semibold">Producción y exportación anual</h2><div className="mt-2 flex flex-wrap gap-4 text-xs text-ink-600"><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-sm bg-brand-800" />Producción / salida</span><span className="flex items-center gap-2"><span className="h-3 w-3 rounded-sm bg-ink-400" />Volumen exportado</span><span>u. sim.</span></div>
      <div className="mt-4 overflow-x-auto"><svg width={width} height="240" className="min-w-full" role="img" aria-label="Producción y exportación anual del nodo seleccionado. Valores exactos en la tabla de detalle.">
        {[0, 0.5, 1].map(f => <g key={f}><line x1="65" x2={width} y1={190 - f * 155} y2={190 - f * 155} className="stroke-ink-200" /><text x="58" y={194 - f * 155} textAnchor="end" className="fill-ink-500 text-xs">{new Intl.NumberFormat('es-AR', { notation: 'compact', maximumFractionDigits: 1 }).format(max * f)}</text></g>)}
        {rows.map((r, i) => { const x = 70 + (i + 0.5) * (width - 70) / rows.length; return <g key={r.period}>{(['output', 'exported'] as const).map((field, j) => {
          const value = r[field]; if (value == null || !Number.isFinite(value)) return null;
          const h = Math.max(0, value) / max * 155;
          return <rect key={field} x={x - 23 + j * 24} y={190 - h} width="20" height={h} rx="3" className={j ? 'fill-ink-400' : 'fill-brand-800'}><title>{operationalYear(evaluation, r.period)} · {field === 'output' ? 'Salida' : 'Exportado'}: {operationalNumber(value)} u. sim.</title></rect>;
        })}<text x={x} y="215" textAnchor="middle" className="fill-ink-600 text-xs">{evaluation.snapshot.configuration ? operationalYear(evaluation, r.period) : 'P' + r.period}</text></g>; })}
      </svg></div><p className="mt-2 text-xs text-ink-500">Las exportaciones son una métrica independiente: no se suman a la producción. Los datos ausentes no se dibujan como cero.</p>
    </Card>
    {rows.some(r => operationalColumns.some(([f]) => r[f] == null)) && <Alert>Hay valores no disponibles en esta evaluación. Se conservan como datos ausentes.</Alert>}
    <Card><h2 className="mb-4 text-lg font-semibold">Detalle anual del nodo</h2><div className="overflow-x-auto"><table className="w-full text-right text-sm"><thead><tr><th scope="col" className="whitespace-nowrap p-3 text-left">Año / período</th>{operationalColumns.map(([key, label, unit]) => <th scope="col" key={key} className="p-3">{label}<span className="block whitespace-nowrap text-xs font-normal text-ink-500">{unit}</span></th>)}</tr></thead><tbody>{rows.map(r => <tr key={r.period} className="border-t border-ink-100"><th scope="row" className="whitespace-nowrap p-3 text-left">{operationalYear(evaluation, r.period)} / {r.period}</th>{operationalColumns.map(([field]) => <td key={field} className="whitespace-nowrap p-3 tabular-nums">{operationalNumber(r[field])}</td>)}</tr>)}</tbody></table></div><p className="mt-3 text-xs text-ink-500">Horas y eventos corresponden al nodo seleccionado. El período 1 es el primer año de operación, posterior a la inversión.</p></Card>
  </div>;
}
