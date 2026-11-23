import { useState } from 'react';
import type { ComparisonResult, MetricCell, MetricRow, NodeCell } from '../../types/comparison';
import {
  KEY_METRICS,
  SECONDARY_METRICS,
  STANDING_LABELS,
  TONE_CLASSES,
  DELETED_NOTE,
  cellText,
  deletedCount,
  formatMetric,
  gapText,
  rankLabel,
  groupMetrics,
  nodeTypeLabel,
  roundsNote,
  scenarioNames,
  seriesColor,
  standingTone,
} from '../../utils/comparison';
import { Alert } from '../ui/Alert';
import { ComparisonChart } from './ComparisonChart';
import { ExecutiveSummary } from './ExecutiveSummary';
import { KeyFindings } from './KeyFindings';
import { MetricBars } from './MetricBars';

/**
 * A comparison result, densest at the bottom: the conclusion, the four headline
 * indicators, what matters most in words, the yearly evolution, the second layer of
 * indicators, and finally every number in tables. Scenarios are compared with each
 * other; none is a reference.
 */
export function ComparisonResults({ result }: { result: ComparisonResult }) {
  const names = scenarioNames(result);
  const gone = deletedCount(result);
  return <div className="space-y-8">
    {gone > 0 && <Alert tone="info">
      <p className="font-semibold">{gone === 1 ? '1 escenario eliminado' : `${gone} escenarios eliminados`}</p>
      <p className="mt-1">Se {DELETED_NOTE} Los marcados «(eliminado)» ya no existen, pero la comparación conserva su simulación.</p>
    </Alert>}
    <ExecutiveSummary result={result} names={names} />

    {result.warnings.length > 0 && <Alert tone="warning">
      <p className="font-semibold">Para leer con cuidado</p>
      <ul className="mt-1 list-disc pl-5">
        {result.warnings.map(w => <li key={w.code}>{w.message} <span className="text-warn-700">({w.evaluationIds.map(id => names[id]).join(', ')})</span></li>)}
      </ul>
    </Alert>}

    <section aria-labelledby="key-indicators" className="space-y-4">
      <h2 id="key-indicators" className="text-lg font-semibold text-ink-800">Indicadores clave</h2>
      <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
        {KEY_METRICS.map(metric => <MetricBars key={metric} result={result} metric={metric} names={names} />)}
      </div>
    </section>

    <div className="grid grid-cols-1 gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.6fr)] xl:items-start">
      <KeyFindings findings={result.findings} />
      <ComparisonChart result={result} names={names} />
    </div>

    <section aria-labelledby="more-indicators" className="space-y-4">
      <h2 id="more-indicators" className="text-lg font-semibold text-ink-800">Más indicadores</h2>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {SECONDARY_METRICS.map(metric => <MetricBars key={metric} result={result} metric={metric} names={names} size="sm" />)}
      </div>
    </section>

    <ComparisonDetails result={result} names={names} />
  </div>;
}

function StandingChip({ row, cell, currency }: { row: MetricRow; cell: MetricCell; currency?: string | null }) {
  if (cell.standing === 'UNRANKED' || cell.standing === 'NOT_COMPARABLE') return null;
  const text = gapText(row, cell, currency) ?? STANDING_LABELS[cell.standing];
  return <span className={`relative inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-xs font-medium ${TONE_CLASSES[standingTone(cell.standing)]}`}>
    {rankLabel(cell.rank) && cell.standing !== 'TIED' && <span className="tabular-nums">{rankLabel(cell.rank)} ·</span>}{text}
  </span>;
}

/** Every indicator and every node, for whoever needs the exact figures. Collapsed by default. */
function ComparisonDetails({ result, names }: { result: ComparisonResult; names: Record<string, string> }) {
  const [relevantNodesOnly, setRelevantNodesOnly] = useState(false);
  const currencyOf = (evaluationId: string) => result.scenarios.find(s => s.evaluationId === evaluationId)?.currency;
  const groups = groupMetrics(result.metrics);
  const nodes = relevantNodesOnly ? result.nodes.filter(n => n.relevant) : result.nodes;
  const header = <tr className="border-y border-ink-200 bg-ink-50">
    <th scope="col" className="p-3 font-semibold text-ink-700">&nbsp;</th>
    {result.scenarios.map((s, index) => <th key={s.evaluationId} scope="col" className="p-3 font-semibold text-ink-700">
      <span className="flex items-center gap-2">
        <span aria-hidden="true" className={`h-2.5 w-2.5 shrink-0 rounded-full ${seriesColor(index).bg}`} />{names[s.evaluationId]}
      </span>
    </th>)}
  </tr>;

  return <details className="group rounded-card border border-ink-100 bg-surface shadow-raise">
    <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-6">
      <span>
        <span className="block text-lg font-semibold text-ink-800">Detalle completo</span>
        <span className="block text-sm text-ink-500">Todos los indicadores con la posición de cada escenario y los flujos de cada nodo.</span>
      </span>
      <span className="text-sm font-semibold text-brand-800 group-open:hidden">Mostrar</span>
      <span className="hidden text-sm font-semibold text-brand-800 group-open:inline">Ocultar</span>
    </summary>
    <div className="space-y-8 border-t border-ink-100 pb-6">
      <div className="overflow-x-auto">
        <table className="w-full min-w-[640px] text-left text-sm">
          <thead>{header}</thead>
          {groups.map(group => <tbody key={group.category}>
            <tr><th colSpan={result.scenarios.length + 1} scope="colgroup"
              className="px-3 pt-5 pb-2 text-xs font-semibold uppercase tracking-wide text-ink-500">{group.label}</th></tr>
            {group.rows.map(row => <tr key={row.metric} className="border-b border-ink-100 align-top">
              <th scope="row" className="p-3 font-medium text-ink-800">{row.label}</th>
              {row.values.map(cell => {
                const best = row.bestEvaluationIds.includes(cell.evaluationId);
                const note = roundsNote(cell);
                return <td key={cell.evaluationId} className="p-3">
                  <div className="flex flex-col items-start gap-1">
                    <span className={`tabular-nums ${cell.value.value == null ? 'text-ink-500' : 'text-ink-800'} ${best ? 'font-semibold' : ''}`}>
                      {cellText(row, cell, currencyOf(cell.evaluationId))}
                    </span>
                    <StandingChip row={row} cell={cell} currency={currencyOf(cell.evaluationId)} />
                    {note && <span className="text-xs text-ink-500">{note}</span>}
                  </div>
                </td>;
              })}
            </tr>)}
          </tbody>)}
        </table>
      </div>

      <div className="space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-3 px-6">
          <h3 className="text-base font-semibold text-ink-800">Flujos por nodo</h3>
          <label className="flex items-center gap-2 text-sm text-ink-700">
            <input type="checkbox" className="h-4 w-4 accent-brand-800" checked={relevantNodesOnly}
              onChange={event => setRelevantNodesOnly(event.target.checked)} />
            Solo nodos con diferencias relevantes
          </label>
        </div>
        {!nodes.length ? <p className="px-6 text-sm text-ink-600">
          {result.nodes.length ? 'Ningún nodo tiene diferencias relevantes.' : 'Las simulaciones no guardan flujos por nodo.'}
        </p> : <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead>{header}</thead>
            <tbody>{nodes.map(node => <tr key={node.nodeKey} className="border-b border-ink-100 align-top">
              <th scope="row" className="p-3 font-medium text-ink-800">{node.name}
                {node.nodeType && <span className="block text-xs font-normal text-ink-500">{nodeTypeLabel(node.nodeType)}</span>}</th>
              {node.values.map(cell => <NodeValue key={cell.evaluationId} cell={cell} />)}
            </tr>)}</tbody>
          </table>
        </div>}
      </div>
    </div>
  </details>;
}

function NodeValue({ cell }: { cell: NodeCell }) {
  if (!cell.present) return <td className="p-3 text-ink-500">No está en este escenario</td>;
  return <td className="p-3">
    <div className="flex flex-col items-start gap-1">
      <span className="tabular-nums text-ink-800">{formatMetric('SIMULATOR_UNIT', cell.flows?.output)}</span>
      {cell.flows && <span className="text-xs text-ink-500">Pérdidas {formatMetric('SIMULATOR_UNIT', cell.flows.losses)} · {formatMetric('HOURS', cell.flows.operatingHours)}</span>}
    </div>
  </td>;
}
