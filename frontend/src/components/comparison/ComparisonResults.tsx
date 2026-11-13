import { useState } from 'react';
import type { ComparisonResult, MetricCell, MetricRow, NodeCell } from '../../types/comparison';
import {
  ASSESSMENT_LABELS,
  TONE_CLASSES,
  assessmentTone,
  cellText,
  deltaText,
  findingTone,
  formatMetric,
  groupMetrics,
  nodeTypeLabel,
  roundsNote,
  scenarioNames,
} from '../../utils/comparison';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { ComparisonChart } from './ComparisonChart';

const FINDINGS_SHOWN = 8;

function Toggle({ checked, onChange, label }: { checked: boolean; onChange: (value: boolean) => void; label: string }) {
  return <label className="flex items-center gap-2 text-sm text-ink-700">
    <input type="checkbox" className="h-4 w-4 accent-brand-800" checked={checked} onChange={event => onChange(event.target.checked)} />
    {label}
  </label>;
}

function DeltaChip({ row, cell }: { row: MetricRow; cell: MetricCell }) {
  const text = deltaText(row.unit, cell);
  if (cell.assessment === 'BASELINE') return null;
  const tone = assessmentTone(cell.assessment);
  return <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}
    title={ASSESSMENT_LABELS[cell.assessment]}>
    {text ?? ASSESSMENT_LABELS[cell.assessment]}
    {text && <span className="sr-only">({ASSESSMENT_LABELS[cell.assessment]})</span>}
  </span>;
}

/** Everything the comparison returns: who wins, what differs, indicator table, annual chart and node flows. */
export function ComparisonResults({ result }: { result: ComparisonResult }) {
  const [relevantOnly, setRelevantOnly] = useState(false);
  const [relevantNodesOnly, setRelevantNodesOnly] = useState(true);
  const [allFindings, setAllFindings] = useState(false);
  const names = scenarioNames(result);
  const currencyOf = (evaluationId: string) => result.scenarios.find(s => s.evaluationId === evaluationId)?.currency;
  const groups = groupMetrics(result.metrics, relevantOnly);
  const findings = allFindings ? result.findings : result.findings.slice(0, FINDINGS_SHOWN);
  const nodes = relevantNodesOnly ? result.nodes.filter(n => n.relevant) : result.nodes;
  const percent = (value: number) => new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 }).format(value * 100);

  // Block layout, not flex: a flex item would grow to its wide table instead of letting the table scroll.
  return <div className="space-y-6">
    <section aria-labelledby="comparison-scenarios" className="flex flex-col gap-3">
      <h2 id="comparison-scenarios" className="text-lg font-semibold text-ink-800">Escenarios comparados</h2>
      <ul className="grid gap-4 sm:grid-cols-2 xl:grid-cols-3">
        {result.scenarios.map(s => <li key={s.evaluationId}>
          <Card className="h-full">
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-semibold text-ink-800">{names[s.evaluationId]}</span>
              {s.baseline && <span className="rounded-full bg-brand-50 px-2 py-0.5 text-xs font-medium text-brand-800">Línea base</span>}
            </div>
            <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-1 text-sm">
              <dt className="text-ink-500">Evaluada</dt><dd className="text-ink-700">{new Date(s.evaluatedAt).toLocaleString('es-AR', { dateStyle: 'short', timeStyle: 'short' })}</dd>
              <dt className="text-ink-500">Tipo</dt><dd className="text-ink-700">{s.mode === 'PROBABILISTIC' ? `Probabilística (${s.rounds} rondas)` : 'Determinista'}</dd>
              <dt className="text-ink-500">Horizonte</dt><dd className="text-ink-700">{s.startYear != null && s.years != null ? `${s.startYear}–${s.startYear + s.years}` : 'No disponible'}</dd>
              <dt className="text-ink-500">Moneda · WACC</dt><dd className="text-ink-700">{s.currency ?? '—'} · {formatMetric('FRACTION', s.wacc)}</dd>
            </dl>
            <p className="mt-3 text-sm text-ink-600">
              {s.bestIn.length ? <>Mejor en <span className="font-semibold text-brand-800">{s.bestIn.length}</span> indicador{s.bestIn.length === 1 ? '' : 'es'}</> : 'No lidera ningún indicador'}
            </p>
          </Card>
        </li>)}
      </ul>
    </section>

    {result.warnings.length > 0 && <Alert tone="warning">
      <p className="font-semibold">Tené en cuenta antes de comparar:</p>
      <ul className="mt-1 list-disc pl-5">
        {result.warnings.map(w => <li key={w.code}>{w.message} <span className="text-warn-700">({w.evaluationIds.map(id => names[id]).join(', ')})</span></li>)}
      </ul>
    </Alert>}

    <Card>
      <h2 className="text-lg font-semibold text-ink-800">Diferencias relevantes</h2>
      <p className="mt-1 text-sm text-ink-500">
        Contra la línea base, con umbrales de {percent(result.relevanceThreshold)} % para montos y volúmenes y {percent(result.rateThreshold)} p.p. para tasas.
      </p>
      {!result.findings.length ? <p className="mt-4 text-sm text-ink-600">No hay diferencias relevantes con estos umbrales.</p> : <>
        <ul className="mt-4 flex flex-col gap-2">
          {findings.map((finding, index) => <li key={index}
            className={`rounded-control border px-3 py-2 text-sm ${TONE_CLASSES[findingTone(finding.type)]}`}>{finding.message}</li>)}
        </ul>
        {result.findings.length > FINDINGS_SHOWN && <button type="button" className="mt-3 text-sm text-brand-800 underline"
          onClick={() => setAllFindings(value => !value)}>
          {allFindings ? 'Ver menos' : `Ver todas (${result.findings.length})`}
        </button>}
      </>}
    </Card>

    <Card padded={false}>
      <div className="flex flex-wrap items-center justify-between gap-3 p-6 pb-4">
        <div>
          <h2 className="text-lg font-semibold text-ink-800">Indicadores</h2>
          <p className="mt-1 text-sm text-ink-500">Valores de cada escenario y su diferencia con la línea base. ★ marca el mejor valor.</p>
        </div>
        <Toggle checked={relevantOnly} onChange={setRelevantOnly} label="Solo indicadores con diferencias relevantes" />
      </div>
      {!groups.length ? <p className="px-6 pb-6 text-sm text-ink-600">Ningún indicador tiene diferencias relevantes.</p> :
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead><tr className="border-y border-ink-200 bg-ink-50">
              <th scope="col" className="p-3 font-semibold text-ink-700">Indicador</th>
              {result.scenarios.map(s => <th key={s.evaluationId} scope="col" className="p-3 font-semibold text-ink-700">
                {names[s.evaluationId]}{s.baseline && <span className="block text-xs font-normal text-ink-500">Línea base</span>}
              </th>)}
            </tr></thead>
            {groups.map(group => <tbody key={group.category}>
              <tr><th colSpan={result.scenarios.length + 1} scope="colgroup"
                className="bg-surface px-3 pt-4 pb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">{group.label}</th></tr>
              {group.rows.map(row => <tr key={row.metric} className="border-b border-ink-100 align-top">
                <th scope="row" className="p-3 font-medium text-ink-800">{row.label}</th>
                {row.values.map(cell => {
                  const best = row.bestEvaluationIds.includes(cell.evaluationId);
                  const note = roundsNote(cell);
                  return <td key={cell.evaluationId} className={`p-3 ${cell.relevant ? 'bg-warn-50/60' : ''}`}>
                    <div className="flex flex-col items-start gap-1">
                      <span className={`tabular-nums ${cell.value.value == null ? 'text-ink-500' : 'text-ink-800'} ${best ? 'font-semibold' : ''}`}>
                        {best && <span aria-label="Mejor valor" className="mr-1 text-brand-800">★</span>}
                        {cellText(row, cell, currencyOf(cell.evaluationId))}
                      </span>
                      <DeltaChip row={row} cell={cell} />
                      {note && <span className="text-xs text-ink-500">{note}</span>}
                    </div>
                  </td>;
                })}
              </tr>)}
            </tbody>)}
          </table>
        </div>}
    </Card>

    <Card>
      <h2 className="text-lg font-semibold text-ink-800">Evolución anual</h2>
      <p className="mt-1 mb-4 text-sm text-ink-500">Flujos de caja y volúmenes año por año. Un año sin datos corta la línea; no se toma como cero.</p>
      <ComparisonChart result={result} names={names} />
    </Card>

    <Card padded={false}>
      <div className="flex flex-wrap items-center justify-between gap-3 p-6 pb-4">
        <div>
          <h2 className="text-lg font-semibold text-ink-800">Flujos por nodo</h2>
          <p className="mt-1 text-sm text-ink-500">Salida total de cada nodo en el horizonte (unidades del simulador), emparejados entre escenarios.</p>
        </div>
        <Toggle checked={relevantNodesOnly} onChange={setRelevantNodesOnly} label="Solo nodos con diferencias relevantes" />
      </div>
      {!nodes.length ? <p className="px-6 pb-6 text-sm text-ink-600">
        {result.nodes.length ? 'Ningún nodo tiene diferencias relevantes.' : 'Las evaluaciones no guardan flujos por nodo.'}
      </p> :
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead><tr className="border-y border-ink-200 bg-ink-50">
              <th scope="col" className="p-3 font-semibold text-ink-700">Nodo</th>
              {result.scenarios.map(s => <th key={s.evaluationId} scope="col" className="p-3 font-semibold text-ink-700">{names[s.evaluationId]}</th>)}
            </tr></thead>
            <tbody>{nodes.map(node => <tr key={node.nodeKey} className="border-b border-ink-100 align-top">
              <th scope="row" className="p-3 font-medium text-ink-800">{node.name}
                {node.nodeType && <span className="block text-xs font-normal text-ink-500">{nodeTypeLabel(node.nodeType)}</span>}</th>
              {node.values.map((cell, index) => <NodeValue key={cell.evaluationId} cell={cell} baseline={index === 0} />)}
            </tr>)}</tbody>
          </table>
        </div>}
    </Card>
  </div>;
}

function NodeValue({ cell, baseline }: { cell: NodeCell; baseline: boolean }) {
  if (!cell.present) {
    return <td className={`p-3 text-ink-500 ${cell.relevant ? 'bg-warn-50/60' : ''}`}>No está en este escenario</td>;
  }
  const delta = cell.outputDelta;
  // More output at one node is not better or worse by itself (it may just be flow moved along the chain).
  const tone = delta == null || delta === 0 ? 'muted' : 'neutral';
  return <td className={`p-3 ${cell.relevant ? 'bg-warn-50/60' : ''}`}>
    <div className="flex flex-col items-start gap-1">
      <span className="tabular-nums text-ink-800">{formatMetric('SIMULATOR_UNIT', cell.flows?.output)}</span>
      {!baseline && delta != null && <span className={`rounded-full border px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[tone]}`}>
        {deltaText('SIMULATOR_UNIT', { absoluteDelta: delta, relativeDelta: cell.outputRelativeDelta, assessment: 'CHANGED' })}
      </span>}
      {cell.flows && <span className="text-xs text-ink-500">Pérdidas {formatMetric('SIMULATOR_UNIT', cell.flows.losses)} · {formatMetric('HOURS', cell.flows.operatingHours)}</span>}
    </div>
  </td>;
}
