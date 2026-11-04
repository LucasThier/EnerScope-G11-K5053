import { useEffect, useState } from 'react';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import type { Evaluation, EconomicRound, NumericStatistics } from '../../types/economics';
import { money, percent } from '../../utils/economicFormat';
import { operationalColumns, operationalDisplay, operationalNumber } from '../../utils/operationalResults';
import { roundEvaluation } from '../../utils/probabilisticResults';
import { AnnualBandChart, PercentileRange, RecoveryBar, StatisticsDetails, SummaryMetric } from './SummaryVisuals';
import { finiteValue, recoveryProbability } from '../../utils/summaryVisuals';
import { ScenarioResults } from './ScenarioResults';
import { downloadCsv, summaryCsv } from '../../utils/resultsCsv';
import { Button } from '../ui/Button';
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
  const [metric, setMetric] = useState('output');
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
    return { period:m.period, metric:m.metric, unit:display.units[m.metric], year:evaluation.snapshot.configuration?.startYear == null ? `Período ${m.period}` : String(evaluation.snapshot.configuration.startYear+m.period), label: `${evaluation.snapshot.configuration?.startYear == null ? `Período ${m.period}` : evaluation.snapshot.configuration.startYear+m.period} · ${label} (${display.units[m.metric]})`, values };
  });
  const operationalSeries=operational.filter(p=>p.metric===metric);
  const discountedProbability=recoveryProbability(summary.discountedPayback.denominator,summary.discountedPayback.recoveredProbability);
  const cashFormat=(n:number|null)=>money(n,currency);
  return <div className="space-y-5">
    <Card><label className="block text-sm font-semibold" htmlFor="round-selection">Vista de resultados</label>
      <div className="mt-2 flex flex-wrap items-center justify-between gap-3"><select id="round-selection" value={selection} onChange={e => { setSelection(e.target.value); setError(''); setRound(null); }} className="w-full rounded-lg border border-ink-200 bg-white p-3 sm:max-w-sm">
        <option value="summary">Resumen de {summary.rounds} rondas</option>
        {Array.from({ length: summary.rounds }, (_, i) => <option key={i + 1} value={i + 1}>Ronda {i + 1}</option>)}
      </select><Button variant="secondary" onClick={() => downloadCsv('resumen-rondas-' + evaluation.id, summaryCsv(evaluation))}>Descargar resumen CSV</Button></div><p className="mt-3 text-sm text-ink-600">{summary.rounds} rondas · {currency ?? 'Moneda no disponible'} · WACC {percent(evaluation.snapshot.configuration?.wacc)}</p>
      <details className="mt-3 text-xs text-ink-500"><summary className="cursor-pointer">Datos de la ejecución</summary><p className="mt-2 break-all">Semilla principal: {evaluation.snapshot.simulation?.seed ?? 'No disponible'}</p><p>Algoritmo: {evaluation.snapshot.simulation?.algorithm ?? 'No disponible'} · Versión de cálculo: {evaluation.snapshot.simulation?.calculationVersion ?? 'No disponible'}</p></details>
    </Card>
    {error && <Alert tone="error">{error}</Alert>}
    {selection !== 'summary' ? round && round.round === Number(selection) ?
      <><p className="text-sm text-ink-600">Ronda {round.round} · Semilla {round.seed}</p><ScenarioResults evaluation={roundEvaluation(evaluation, round)} round={round.round} /></>
      : !error && <p role="status">Cargando ronda…</p> : <>
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <SummaryMetric label="VAN medio" value={cashFormat(summary.npv.count>0?summary.npv.mean:null)} caption={`Muestra: ${summary.npv.count} de ${summary.rounds} rondas`}/>
        <SummaryMetric label="Rondas con VAN positivo" value={percent(recoveryProbability(summary.rounds,summary.positiveNpvProbability))} caption={`Sobre ${summary.rounds} rondas simuladas`}/>
        <SummaryMetric label="TIR mediana" value={percent(summary.irr.count>0?summary.irr.p50:null)} caption={`Calculable en ${summary.irr.count} de ${summary.rounds} rondas`}/>
        <SummaryMetric label="Recuperación descontada" value={percent(discountedProbability)} caption={`Sobre ${summary.discountedPayback.denominator} rondas aplicables con resultado determinado`}/>
      </div>
      <p className="text-xs leading-relaxed text-ink-500">P10: bajo · P50: mediana · P90: alto. Las frecuencias describen esta muestra. Las bandas anuales no representan una ronda individual ni un intervalo de confianza.</p>
      <Card><h2 className="text-lg font-semibold">Rentabilidad</h2>
        <div className="mt-5 grid gap-8 md:grid-cols-2"><PercentileRange label="VAN" values={summary.npv} format={cashFormat}/><PercentileRange label="TIR anual calculable" values={summary.irr} format={percent}/></div>
        <p className="mt-4 text-xs text-ink-500">TIR calculable: {summary.irr.count} de {summary.rounds} rondas. Estados: {Object.entries(summary.irrStatuses).map(([s,n]) => `${statusLabels[s] ?? s}: ${n}`).join(' · ')}.</p>
        <StatisticsDetails><StatisticsTable rows={[{label:'VAN',values:summary.npv}]} format={cashFormat}/><StatisticsTable rows={[{label:'TIR anual calculable',values:summary.irr}]} format={percent}/></StatisticsDetails>
      </Card>
      <Card><h2 className="text-lg font-semibold">Recuperación de la inversión</h2>
        <div className="mt-5 grid gap-6 md:grid-cols-2">{([{label:'Simple',value:summary.simplePayback},{label:'Descontada',value:summary.discountedPayback}]).map(({label,value:p})=>{
          const probability=recoveryProbability(p.denominator,p.recoveredProbability);
          const median=p.recoveryPeriods.count>0&&finiteValue(p.recoveryPeriods.p50)?p.recoveryPeriods.p50:null;
          const start=evaluation.snapshot.configuration?.startYear;
          return <section key={label} className="min-w-0 rounded-xl border border-ink-100 p-4">
            <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold">{label}</h3><span className="font-semibold">{percent(probability)} recuperada</span></div>
            <RecoveryBar label={`Recuperación ${label.toLowerCase()}`} value={probability}/>
            <p className="text-xs text-ink-500">Base: {p.denominator} rondas aplicables con resultado determinado.</p>
            <p className="mt-3 text-sm">{median===null?'Recuperación mediana: No disponible':`Mediana: período ${median}${start==null?'':` · Año ${start+median}`}`}</p>
            <p className="mt-1 text-xs text-ink-500">Solo las {p.recoveryPeriods.count} rondas recuperadas.</p>
            <p className="mt-3 text-xs text-ink-600">{Object.entries(p.statuses).map(([s,n])=>`${statusLabels[s]??s}: ${n}`).join(' · ')}</p>
            <p className="mt-2 text-xs text-ink-500">{p.becomesNegativeAgainCount} vuelven a saldo negativo después de recuperar.</p>
            <StatisticsDetails><StatisticsTable rows={[{label:'Período de recuperación (solo recuperadas)',values:p.recoveryPeriods}]} format={operationalNumber}/></StatisticsDetails>
          </section>;
        })}</div>
      </Card>
      <Card><h2 className="text-lg font-semibold">Dispersión de flujos anuales</h2>
        <AnnualBandChart label="Flujos anuales: mediana y rango P10–P90" points={summary.cashFlows.map(p=>({period:p.period,label:String(p.year),values:p.values}))} format={cashFormat} unit={currency??'Moneda no disponible'}/>
        <StatisticsDetails><StatisticsTable rows={summary.cashFlows.map(p=>({label:String(p.year),values:p.values}))} format={cashFormat}/></StatisticsDetails>
      </Card>
      <Card><h2 className="text-lg font-semibold">Rangos operativos por nodo</h2>
        <div className="mt-4 grid gap-3 sm:grid-cols-2"><div className="flex min-w-0 flex-col gap-1 text-sm"><label htmlFor="summary-node">Nodo</label><select id="summary-node" value={selectedNode??''} onChange={e=>setNode(e.target.value)} className="w-full min-w-0 rounded-lg border border-ink-200 bg-white p-2">{nodeIds.map(id=><option key={id} value={id}>{names.get(id)||id}</option>)}</select></div>
        <div className="flex min-w-0 flex-col gap-1 text-sm"><label htmlFor="summary-metric">Métrica</label><select id="summary-metric" value={metric} onChange={e=>setMetric(e.target.value)} className="w-full rounded-lg border border-ink-200 bg-white p-2">{operationalColumns.map(([key,label])=><option key={key} value={key}>{label}</option>)}</select></div></div>
        <AnnualBandChart key={selectedNode+metric} label="Operación anual del nodo: mediana y rango P10–P90" points={operationalSeries.map(p=>({period:p.period,label:p.year,values:p.values}))} format={operationalNumber} unit={operationalSeries[0]?.unit??'Unidad no disponible'}/>
        <StatisticsDetails><StatisticsTable rows={operational} format={operationalNumber}/></StatisticsDetails>
      </Card>
    </>}
  </div>;
}
