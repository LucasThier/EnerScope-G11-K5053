import { useEffect, useState } from 'react';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import type { Evaluation, EconomicRound, NumericStatistics, PaybackStatistics } from '../../types/economics';
import { money, percent } from '../../utils/economicFormat';
import { operationalColumns, operationalDisplay, operationalNumber } from '../../utils/operationalResults';
import { roundEvaluation } from '../../utils/probabilisticResults';
import { ScenarioResults } from './ScenarioResults';
import { downloadCsv, summaryCsv } from '../../utils/resultsCsv';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { Alert } from '../ui/Alert';
import { SelectField } from '../ui/SelectField';
import { BandChart, PercentileRange, StatisticalDetail, SummaryCard } from './SummaryVisuals';
import { finite, recoveryView } from '../../utils/summaryVisuals';
import type { OperationalField } from '../../utils/operationalResults';

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
  const [metric, setMetric] = useState<OperationalField>('output');
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
    const year = evaluation.snapshot.configuration?.startYear;
    return { period: m.period, metric: m.metric, unit: display.units[m.metric], year: finite(year) ? String(year + m.period) : `Período ${m.period}`, label: `${finite(year) ? year + m.period : 'Período ' + m.period} · ${label} (${display.units[m.metric]})`, values };
  });
  const selectedMetric = operational.filter(row => row.metric === metric);
  const discounted = recoveryView(summary.discountedPayback, evaluation.snapshot.configuration?.startYear);
  return <div className="space-y-5">
    <Card><label className="block text-sm font-semibold" htmlFor="round-selection">Vista de resultados</label>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3"><select id="round-selection" value={selection} onChange={e => { setSelection(e.target.value); setError(''); setRound(null); }} className="w-full rounded-lg border border-ink-200 bg-white p-3 sm:max-w-sm">
        <option value="summary">Resumen de {summary.rounds} rondas</option>
        {Array.from({ length: summary.rounds }, (_, i) => <option key={i + 1} value={i + 1}>Ronda {i + 1}</option>)}
      </select><Button variant="secondary" onClick={() => downloadCsv('resumen-rondas-' + evaluation.id, summaryCsv(evaluation))}>Descargar resumen CSV</Button></div><p className="mt-3 text-sm text-ink-600">{summary.rounds} rondas · {currency ?? 'Moneda no disponible'} · WACC anual: {percent(evaluation.snapshot.configuration?.wacc)}</p>
      <details className="mt-3 text-sm"><summary className="cursor-pointer rounded focus-visible:outline-2 focus-visible:outline-brand-800">Datos de la ejecución</summary><p className="mt-2 break-all">Semilla principal: {evaluation.snapshot.simulation?.seed ?? 'No disponible'}</p><p>Versión: {evaluation.snapshot.simulation?.calculationVersion ?? 'No disponible'}</p></details>
    </Card>
    {error && <Alert tone="error">{error}</Alert>}
    {selection !== 'summary' ? round && round.round === Number(selection) ?
      <><p className="text-sm text-ink-600">Ronda {round.round} · Semilla {round.seed}</p><ScenarioResults evaluation={roundEvaluation(evaluation, round)} round={round.round} /></>
      : !error && <p role="status">Cargando ronda…</p> : <>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryCard title="VAN medio" value={money(summary.npv.count > 0 ? summary.npv.mean : null, currency)} caption={`${summary.npv.count} de ${summary.rounds} rondas con VAN disponible`} />
        <SummaryCard title="Rondas con VAN positivo" value={percent(summary.rounds > 0 ? summary.positiveNpvProbability : null)} caption={`Sobre ${summary.rounds} rondas simuladas`} />
        <SummaryCard title="TIR mediana" value={percent(summary.irr.count > 0 ? summary.irr.p50 : null)} caption={`${summary.irr.count} de ${summary.rounds} rondas calculables`} />
        <SummaryCard title="Recuperación descontada" value={percent(discounted.probability)} caption={`${summary.discountedPayback.recoveryPeriods.count} recuperadas / ${summary.discountedPayback.denominator} aplicables con resultado determinado`} />
      </div>
      <p className="text-sm text-ink-600">P10 es bajo, P50 es la mediana y P90 es alto. Las frecuencias describen las rondas simuladas. Las bandas anuales son estadísticas por año: no representan una trayectoria individual ni un intervalo de confianza.</p>
      <Card><h2 className="mb-5 text-lg font-semibold">Rentabilidad</h2>
        <div className="grid gap-8 lg:grid-cols-2"><PercentileRange title={`VAN · ${currency ?? 'Moneda no disponible'}`} values={summary.npv} format={n => money(n, currency)} /><PercentileRange title="TIR anual" values={summary.irr} format={percent} /></div>

        <StatisticalDetail><StatisticsTable rows={[{ label: 'VAN', values: summary.npv }]} format={n => money(n, currency)} /><StatisticsTable rows={[{ label: 'TIR anual calculable', values: summary.irr }]} format={percent} /><p className="mt-3 text-sm text-ink-500">{Object.entries(summary.irrStatuses).map(([status,count]) => `${statusLabels[status] ?? status}: ${count}`).join(' · ')}</p></StatisticalDetail>
      </Card>
      <Card><h2 className="mb-5 text-lg font-semibold">Recuperación de la inversión</h2>
        <div className="grid gap-8 md:grid-cols-2"><Recovery label="Payback simple" value={summary.simplePayback} startYear={evaluation.snapshot.configuration?.startYear} /><Recovery label="Payback descontado" value={summary.discountedPayback} startYear={evaluation.snapshot.configuration?.startYear} /></div>
      </Card>
      <Card><h2 className="text-lg font-semibold">Flujos anuales</h2>
        <BandChart title="Flujos anuales" points={summary.cashFlows.map(p => ({period:p.period,label:String(p.year),values:p.values}))} format={n => money(n,currency)} unit={currency ?? 'Moneda no disponible'} />
        <StatisticalDetail><StatisticsTable rows={summary.cashFlows.map(p => ({ label: `${p.year} · Período ${p.period}`, values: p.values }))} format={n => money(n, currency)} /></StatisticalDetail>
      </Card>
      <Card><h2 className="mb-3 text-lg font-semibold">Operación por nodo</h2>
        <div className="grid gap-4 sm:grid-cols-2"><SelectField label="Nodo" id="summary-node" value={selectedNode ?? ''} onChange={e => setNode(e.target.value)}>{nodeIds.map(id => <option key={id} value={id}>{names.get(id) || id}</option>)}</SelectField>
        <SelectField label="Métrica" id="summary-metric" value={metric} onChange={e => setMetric(e.target.value as OperationalField)}>{operationalColumns.map(([field,label]) => <option key={field} value={field}>{label}</option>)}</SelectField></div>
        <BandChart key={selectedNode + metric} title={`${names.get(selectedNode) ?? selectedNode ?? 'Nodo'} · ${operationalColumns.find(([field]) => field === metric)?.[1]}`} points={selectedMetric.map(row => ({period:row.period,label:row.year,values:row.values}))} format={operationalNumber} unit={selectedMetric[0]?.unit ?? operationalColumns.find(([field]) => field === metric)?.[2] ?? 'No disponible'} />
        <StatisticalDetail><StatisticsTable rows={operational} format={operationalNumber} /></StatisticalDetail>
      </Card>
    </>}
  </div>;
}

function Recovery({ label, value, startYear }: { label: string; value: PaybackStatistics; startYear?: number }) {
  const view = recoveryView(value, startYear);
  return <div className="space-y-3"><h3 className="font-semibold">{label}</h3><p className="text-xl font-semibold tabular-nums">{percent(view.probability)}</p>
    <div role="img" aria-label={`${label}: ${percent(view.probability)} recuperada sobre ${value.denominator} rondas aplicables con resultado determinado`} className="h-4 overflow-hidden rounded-full border border-ink-300 bg-ink-100">{view.probability !== null && <div className="h-full bg-brand-800" style={{width:`${Math.max(0,Math.min(1,view.probability))*100}%`}} />}</div>
    <p className="text-sm">{value.recoveryPeriods.count} recuperadas / {value.denominator} aplicables con resultado determinado.</p>
    <p className="text-sm">Mediana entre recuperadas: {view.period !== null ? `período ${view.period} · ${view.year !== null ? 'año ' + view.year : 'año no disponible'}` : 'No disponible'}.</p>
    <p className="text-sm text-ink-600">No aplicables: {view.notApplicable} · Indeterminadas: {view.undetermined} · Vuelven a saldo negativo: {value.becomesNegativeAgainCount}.</p>
    <StatisticalDetail><StatisticsTable rows={[{label:'Período de recuperación (solo recuperadas)',values:value.recoveryPeriods}]} format={operationalNumber} /><p className="text-sm">{Object.entries(value.statuses).map(([s,n]) => `${statusLabels[s] ?? s}: ${n}`).join(' · ')}</p></StatisticalDetail>
  </div>;
}
