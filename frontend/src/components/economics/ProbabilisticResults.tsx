import { useEffect, useState } from 'react';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import type { Evaluation, EconomicRound, NumericStatistics } from '../../types/economics';
import { money, percent } from '../../utils/economicFormat';
import { operationalColumns, operationalDisplay, operationalNumber } from '../../utils/operationalResults';
import { roundEvaluation } from '../../utils/probabilisticResults';
import { ScenarioResults } from './ScenarioResults';
import { Card } from '../ui/Card';
import { Alert } from '../ui/Alert';

const statusLabels: Record<string, string> = { CALCULATED: 'Calculable', NO_SIGN_CHANGE: 'Sin cambio de signo', NON_CONVENTIONAL: 'Flujos no convencionales', NUMERICAL_FAILURE: 'Fallo numérico', INSUFFICIENT_DATA: 'Datos insuficientes', RECOVERED: 'Recuperada', NOT_RECOVERED_WITHIN_HORIZON: 'Sin recuperar en el horizonte', NOT_APPLICABLE: 'No aplicable' };
const statistics = ['mean','min','max','p10','p50','p90'] as const;
function StatisticsTable({ rows, format }: { rows: { label: string; values: NumericStatistics }[]; format: (n: number | null) => string }) {
  return <div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Indicador / período','Muestra','Media','Mínimo','Máximo','P10','P50','P90'].map(h => <th key={h} className="p-3">{h}</th>)}</tr></thead>
    <tbody>{rows.map(row => <tr key={row.label} className="border-t border-ink-100"><th className="p-3 font-medium">{row.label}</th><td className="p-3">{row.values.count}</td>{statistics.map(key => <td className="whitespace-nowrap p-3" key={key}>{format(row.values[key])}</td>)}</tr>)}</tbody></table></div>;
}
export function ProbabilisticResults({ evaluation, projectId, versionId }: { evaluation: Evaluation; projectId: string; versionId: string }) {
  const [selection, setSelection] = useState('summary');
  const [round, setRound] = useState<EconomicRound | null>(null);
  const [error, setError] = useState('');
  const [node, setNode] = useState('');
  useEffect(() => {
    if (selection === 'summary') return;
    let cancelled = false;
    economicsApi.round(projectId, versionId, evaluation.id, Number(selection))
      .then(value => { if (!cancelled) setRound(value); })
      .catch(e => { if (!cancelled) setError(getErrorMessage(e, 'No se pudo leer la ronda.')); });
    return () => { cancelled = true; };
  }, [projectId, versionId, evaluation.id, selection]);
  const summary = evaluation.snapshot.summary;
  if (!summary) return <Alert>No hay estadísticas disponibles.</Alert>;
  const currency = evaluation.snapshot.configuration?.currency;
  const nodeIds = [...new Set(summary.operational.map(m => m.nodeId))];
  const selectedNode = node || nodeIds[0];
  const names = new Map((evaluation.snapshot.physicalNodes ?? []).map(n => [n.id, n.name || n.id]));
  const operational = summary.operational.filter(m => m.nodeId === selectedNode).map(m => {
    const rows = statistics.map((key, i) => ({ nodeId: m.nodeId, period: i + 1, input: null, output: null, exported: null, losses: null, operatingHours: null, events: null, [m.metric]: m.values[key] }));
    const display = operationalDisplay({ ...evaluation, snapshot: { ...evaluation.snapshot, rawMetrics: rows } }, m.nodeId);
    const values = { ...m.values };
    statistics.forEach((key, i) => { values[key] = display.rows[i][m.metric]; });
    const label = operationalColumns.find(([field]) => field === m.metric)?.[1] ?? m.metric;
    return { label: `${(evaluation.snapshot.configuration?.startYear ?? 0) + m.period} · ${label} (${display.units[m.metric]})`, values };
  });
  return <div className="space-y-5">
    <Card><label className="block text-sm font-semibold" htmlFor="round-selection">Vista de resultados</label>
      <select id="round-selection" value={selection} onChange={e => { setSelection(e.target.value); setError(''); setRound(null); }} className="mt-2 w-full rounded-lg border border-ink-200 bg-white p-3 sm:max-w-sm">
        <option value="summary">Resumen de {summary.rounds} rondas</option>
        {Array.from({ length: summary.rounds }, (_, i) => <option key={i + 1} value={i + 1}>Ronda {i + 1}</option>)}
      </select><p className="mt-3 break-all text-sm text-ink-600">Semilla principal: {evaluation.snapshot.simulation?.seed} · {summary.rounds} rondas</p>
    </Card>
    {error && <Alert tone="error">{error}</Alert>}
    {selection !== 'summary' ? round && round.round === Number(selection) ?
      <><p className="text-sm text-ink-600">Ronda {round.round} · Semilla {round.seed}</p><ScenarioResults evaluation={roundEvaluation(evaluation, round)} /></>
      : !error && <p role="status">Cargando ronda…</p> : <>
      <Alert>P10 representa valores bajos y P90 valores altos. Cada indicador se resume por separado; las bandas anuales no representan una ronda completa. Las probabilidades son frecuencias de esta muestra.</Alert>
      <Card><h2 className="text-lg font-semibold">Rentabilidad</h2><p className="my-3">Rondas con VAN positivo: <strong>{percent(summary.positiveNpvProbability)}</strong> de {summary.rounds}.</p>
        <StatisticsTable rows={[{ label: 'VAN', values: summary.npv }]} format={n => money(n, currency)} />
        <StatisticsTable rows={[{ label: 'TIR anual calculable', values: summary.irr }]} format={percent} />
        <p className="mt-3 text-sm text-ink-600">TIR calculable: {summary.irr.count} de {summary.rounds} rondas. Estados: {Object.entries(summary.irrStatuses).map(([s,n]) => `${statusLabels[s] ?? s}: ${n}`).join(' · ')}.</p>
      </Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Recuperación de la inversión</h2>
        {[['Simple',summary.simplePayback],['Descontada',summary.discountedPayback]] .map(([label, value]) => {
          const p = value as typeof summary.simplePayback;
          return <div key={String(label)} className="mb-5"><h3 className="font-semibold">{String(label)}: {percent(p.recoveredProbability)} recuperada</h3>
            <p className="my-2 text-sm text-ink-600">Base: {p.denominator} rondas aplicables con resultado determinado. {p.becomesNegativeAgainCount} vuelven a saldo negativo después de recuperar.</p>
            <StatisticsTable rows={[{ label: 'Período de recuperación (solo recuperadas)', values: p.recoveryPeriods }]} format={operationalNumber} />
            <p className="text-sm text-ink-600">{Object.entries(p.statuses).map(([s,n]) => `${statusLabels[s] ?? s}: ${n}`).join(' · ')}</p></div>;
        })}
      </Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Dispersión de flujos anuales</h2><StatisticsTable rows={summary.cashFlows.map(p => ({ label: String(p.year), values: p.values }))} format={n => money(n, currency)} /></Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Rangos operativos por nodo</h2>
        <label className="text-sm" htmlFor="summary-node">Nodo</label><select id="summary-node" value={selectedNode ?? ''} onChange={e => setNode(e.target.value)} className="ml-3 max-w-full rounded-lg border border-ink-200 p-2">{nodeIds.map(id => <option key={id} value={id}>{names.get(id) || id}</option>)}</select>
        <StatisticsTable rows={operational} format={operationalNumber} />
      </Card>
    </>}
  </div>;
}
