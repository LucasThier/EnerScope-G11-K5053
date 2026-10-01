import { useState } from 'react';
import type { Evaluation } from '../../types/economics';
import { dateTime } from '../../utils/economicFormat';
import { EconomicResults } from './EconomicResults';
import { OperationalResults } from './OperationalResults';

const tabs = [['operational', 'Operativos'], ['economic', 'Económicos']] as const;
export function ScenarioResults({ evaluation }: { evaluation: Evaluation }) {
  const [tab, setTab] = useState<'operational' | 'economic'>('economic');
  return <div className="space-y-5">
    <div className="flex flex-wrap items-center justify-between gap-4"><div><h2 className="text-lg font-semibold text-ink-800">Resultados del escenario</h2><p className="mt-1 text-sm text-ink-500">Evaluación: {dateTime(evaluation.createdAt)}</p></div>
      <div role="tablist" aria-label="Tipo de resultados" className="flex rounded-xl border border-ink-200 bg-white p-1">{tabs.map(([key, label], i) => <button key={key} type="button" role="tab" id={'tab-' + key} aria-controls={'panel-' + key} aria-selected={tab === key} tabIndex={tab === key ? 0 : -1} onClick={() => setTab(key)} onKeyDown={e => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(e.key)) return;
        e.preventDefault(); const next = e.key === 'Home' ? 0 : e.key === 'End' ? 1 : 1 - i;
        setTab(tabs[next][0]); document.getElementById('tab-' + tabs[next][0])?.focus();
      }} className={'rounded-lg px-4 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-brand-800 ' + (tab === key ? 'bg-brand-50 text-brand-800' : 'text-ink-600 hover:bg-ink-50')}>{label}</button>)}</div>
    </div>
    <div role="tabpanel" id="panel-operational" aria-labelledby="tab-operational" hidden={tab !== 'operational'}><OperationalResults evaluation={evaluation} /></div>
    <div role="tabpanel" id="panel-economic" aria-labelledby="tab-economic" hidden={tab !== 'economic'}><EconomicResults evaluation={evaluation} /></div>
  </div>;
}
