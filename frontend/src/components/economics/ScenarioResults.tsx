import { useState } from 'react';
import type { Evaluation } from '../../types/economics';
import { dateTime } from '../../utils/economicFormat';
import { EconomicResults } from './EconomicResults';
import { OperationalResults } from './OperationalResults';

const tabs = [['operational', 'Operativos'], ['economic', 'Económicos']] as const;
export function ScenarioResults({ evaluation, round }: { evaluation: Evaluation; round?: number }) {
  const [tab, setTab] = useState<'operational' | 'economic'>('economic');
  return <div className="space-y-6">
    <div className="flex justify-end">
      <div role="tablist" aria-label="Tipo de resultados" className="flex rounded-control border border-ink-200 bg-surface p-1">{tabs.map(([key, label], i) => <button key={key} type="button" role="tab" id={'tab-' + key} aria-controls={'panel-' + key} aria-selected={tab === key} tabIndex={tab === key ? 0 : -1} onClick={() => setTab(key)} onKeyDown={e => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
        e.preventDefault(); const next = e.key === 'Home' ? 0 : e.key === 'End' ? 1 : 1 - i;
        setTab(tabs[next][0]); document.getElementById('tab-' + tabs[next][0])?.focus();
      }} className={'rounded-lg px-4 py-3 text-sm font-semibold focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' + (tab === key ? 'bg-brand-50 text-brand-800' : 'text-ink-600 hover:bg-ink-50')}>{label}</button>)}</div>
    </div>
    <div role="tabpanel" id="panel-operational" aria-labelledby="tab-operational" hidden={tab !== 'operational'}><div className="mb-5"><h2 className="text-lg font-semibold text-ink-800">Resultados del escenario</h2><p className="mt-1 text-sm text-ink-500">Evaluación: {dateTime(evaluation.createdAt)}</p></div><OperationalResults evaluation={evaluation} round={round} active={tab === 'operational'} /></div>
    <div role="tabpanel" id="panel-economic" aria-labelledby="tab-economic" hidden={tab !== 'economic'}><EconomicResults evaluation={evaluation} round={round} active={tab === 'economic'} /></div>
  </div>;
}
