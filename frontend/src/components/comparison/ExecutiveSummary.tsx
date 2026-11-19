import type { ComparisonResult } from '../../types/comparison';
import { byRank, compactMetric, formatMetric, headline, metricRow, rankLabel, seriesColor, statusText } from '../../utils/comparison';
import { Card } from '../ui/Card';

/**
 * The first thing a reader sees: which scenario creates the most value (NPV) and by how
 * much it leads the next one, its return and payback, and every scenario ranked by the
 * value it creates. No scenario is a reference.
 */
export function ExecutiveSummary({ result, names }: { result: ComparisonResult; names: Record<string, string> }) {
  const best = headline(result);
  const leader = best ? result.scenarios.find(s => s.evaluationId === best.evaluationId) : undefined;
  const indexOf = (id: string) => result.scenarios.findIndex(s => s.evaluationId === id);
  const currency = leader?.currency;
  const npvRow = metricRow(result, 'NPV');
  const irr = metricRow(result, 'IRR')?.values.find(c => c.evaluationId === best?.evaluationId);
  const payback = metricRow(result, 'SIMPLE_PAYBACK')?.values.find(c => c.evaluationId === best?.evaluationId);
  const percent = new Intl.NumberFormat('es-AR', { style: 'percent', maximumFractionDigits: 0 });

  return <Card elevation="md" padded={false} className="overflow-hidden">
    <div className="grid grid-cols-1 lg:grid-cols-[minmax(0,1.5fr)_minmax(0,1fr)]">
      <div className="flex flex-col gap-5 p-6 sm:p-8">
        <p className="text-sm font-semibold uppercase tracking-wide text-brand-800">Conclusión</p>
        {!best || !leader ? <h2 className="text-2xl font-semibold text-ink-800">
            No hay un valor económico (VAN) comparable entre estos escenarios.</h2> : <>
          <h2 className="text-2xl font-semibold leading-tight text-ink-800 sm:text-3xl">
            <span className="inline-flex items-center gap-3">
              <span aria-hidden="true" className={`h-4 w-4 shrink-0 rounded-full ${seriesColor(indexOf(leader.evaluationId)).bg}`} />
              {names[leader.evaluationId]}
            </span>{' '}
            {best.tied ? 'empata en el primer lugar' : best.allNegative ? 'es el que menos valor pierde' : 'es el escenario que más valor genera'}
          </h2>
          <div>
            <p className="text-sm text-ink-500">Valor que genera (VAN)</p>
            <p className="text-5xl font-semibold tracking-tight text-ink-800 tabular-nums" title={formatMetric('CURRENCY', best.npv, currency)}>
              {compactMetric('CURRENCY', best.npv, currency)}
            </p>
            {best.runnerUpId && best.margin != null && !best.tied && <p className="mt-2 text-base text-ink-600">
              <span className="font-semibold text-ink-800">{compactMetric('CURRENCY', best.margin, currency)} más</span> que el segundo,{' '}
              {names[best.runnerUpId]}{best.relative != null && best.relative > 0 && <> ({percent.format(best.relative)} más)</>}.
            </p>}
          </div>
          <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <div className="rounded-control bg-ink-50 px-4 py-3">
              <dt className="text-xs text-ink-500">Rentabilidad anual (TIR)</dt>
              <dd className="text-xl font-semibold text-ink-800 tabular-nums">
                {irr?.value.value != null ? formatMetric('FRACTION', irr.value.value) : statusText(irr?.value.status ?? 'NOT_AVAILABLE')}</dd>
            </div>
            <div className="rounded-control bg-ink-50 px-4 py-3">
              <dt className="text-xs text-ink-500">Recupera la inversión en</dt>
              <dd className="text-xl font-semibold text-ink-800 tabular-nums">
                {payback?.value.value != null ? formatMetric('YEARS', payback.value.value) : statusText(payback?.value.status ?? 'NOT_AVAILABLE')}</dd>
            </div>
          </dl>
        </>}
      </div>

      <div className="flex flex-col gap-3 border-t border-ink-100 bg-ink-50 p-6 sm:p-8 lg:border-t-0 lg:border-l">
        <p className="text-sm font-semibold text-ink-700">Ranking por valor generado</p>
        <ol className="flex flex-col gap-3">
          {(npvRow ? byRank(npvRow.values) : []).map(cell => {
            const s = result.scenarios.find(x => x.evaluationId === cell.evaluationId)!;
            return <li key={s.evaluationId} className="flex items-start gap-3">
              <span className="w-7 shrink-0 pt-0.5 text-sm font-semibold text-ink-500 tabular-nums">{rankLabel(cell.rank) ?? '—'}</span>
              <span aria-hidden="true" className={`mt-1.5 h-3 w-3 shrink-0 rounded-full ${seriesColor(indexOf(s.evaluationId)).bg}`} />
              <span className="min-w-0 flex-1">
                <span className="flex flex-wrap items-baseline justify-between gap-x-3">
                  <span className="font-medium text-ink-800">{s.versionName}</span>
                  <span className="text-sm font-semibold text-ink-800 tabular-nums">
                    {cell.value.value != null ? compactMetric('CURRENCY', cell.value.value, s.currency) : statusText(cell.value.status)}</span>
                </span>
                <span className="block text-xs text-ink-500">
                  {s.projectName} · {s.mode === 'PROBABILISTIC' ? `${s.rounds} rondas` : 'determinista'} · simulado el{' '}
                  {new Date(s.evaluatedAt).toLocaleDateString('es-AR')}
                </span>
                {s.bestIn.length > 0 && <span className="block text-xs text-ink-600">Mejor en {s.bestIn.length} indicador{s.bestIn.length === 1 ? '' : 'es'}</span>}
              </span>
            </li>;
          })}
        </ol>
      </div>
    </div>
  </Card>;
}
