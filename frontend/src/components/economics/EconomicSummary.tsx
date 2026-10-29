import type { ReactNode } from 'react';
import type { EconomicConfiguration, Indicators, Payback } from '../../types/economics';
import { irrText, money, paybackText } from '../../utils/economicFormat';
import { recoveryMarkers } from '../../utils/economicChart';
import { Card } from '../ui/Card';

function RecoveryRow({ label, value }: { label: string; value: Payback | undefined }) {
  const recovered = value?.status === 'RECOVERED' && value.period != null && value.year != null;
  return <div className="grid grid-cols-[1fr_1.2fr] items-center gap-3 py-4 sm:grid-cols-[1fr_1fr_auto]">
    <span className="text-sm text-ink-600">{label}</span>
    <span className={recovered ? 'text-lg font-semibold text-ink-800' : 'text-sm font-medium text-ink-700 sm:col-span-2'}>{recovered ? value.period + (value.period === 1 ? ' período' : ' períodos') : paybackText(value)}</span>
    {recovered && <span className="col-start-2 text-sm text-ink-500 sm:col-start-auto">Año {value.year}</span>}
  </div>;
}

export function EconomicSummary({ npv, indicators, configuration, heading }: { heading: ReactNode; npv: number | null | undefined; indicators: Indicators | null | undefined; configuration: EconomicConfiguration | null }) {
  const knownNpv = npv != null && Number.isFinite(npv);
  const npvLabel = !knownNpv ? 'VAN' : npv > 0 ? 'VAN Positivo' : npv < 0 ? 'VAN Negativo' : 'VAN Nulo';
  const npvColor = !knownNpv || npv === 0 ? 'bg-ink-100 text-ink-700' : npv > 0 ? 'bg-brand-50 text-brand-800' : 'bg-red-600 text-white';
  const years = configuration?.years ?? 0;
  const start = configuration?.startYear;
  const markers = recoveryMarkers(indicators?.simplePayback, indicators?.discountedPayback, years);
  const ticks = years <= 10 ? Array.from({ length: years + 1 }, (_, i) => i) : [0, Math.round(years / 2), years];
  return <div className="grid items-start gap-5">
    <div className="min-w-0">{heading}</div>
    <Card className="min-w-0 w-full justify-self-start sm:max-w-lg"><h2 className="text-xl font-semibold text-ink-800">Rentabilidad</h2>
      <div className="mt-6 grid gap-y-5 text-center sm:grid-cols-2">
        <div className="min-w-0 px-2"><h3 className={'inline-flex rounded-full px-3 py-1 text-sm font-medium ' + npvColor}>{npvLabel}</h3>
          <p className="mt-3 break-words text-3xl font-semibold tracking-tight text-ink-800">{money(npv, configuration?.currency, true)}</p>
        </div>
        <div className="min-w-0 border-t border-ink-100 px-2 pt-5 sm:border-t-0 sm:border-l sm:pt-0"><h3 className="py-1 text-sm font-medium text-ink-500">TIR anual</h3>
          <p className={'mt-3 break-words font-semibold text-ink-800 ' + (indicators?.irr?.status === 'CALCULATED' ? 'text-3xl tracking-tight' : 'text-base')}>{irrText(indicators?.irr)}</p>
        </div>
      </div>
    </Card>
    <Card className="min-w-0"><h2 className="text-xl font-semibold text-ink-800">Recuperación de la inversión</h2>
      <div className="mt-2 grid items-center gap-5 lg:grid-cols-2"><div className="divide-y divide-ink-100"><RecoveryRow label="Simple" value={indicators?.simplePayback} /><RecoveryRow label="Descontada" value={indicators?.discountedPayback} /></div>
      {start != null && years > 0 && <div className="mt-3 overflow-x-auto"><svg viewBox="0 0 440 90" className="w-full min-w-72" role="img" aria-label={'Línea de recupero: ' + markers.map(m => m.label + ', Año ' + (start + m.period)).join('; ') + '. Horizonte ' + start + ' a ' + (start + years)}>
        <line x1="24" x2="416" y1="40" y2="40" className="stroke-ink-300" strokeWidth="2" />
        {ticks.map(period => <g key={period}><circle cx={24 + period / years * 392} cy="40" r="4" className="fill-white stroke-ink-300" /><text x={24 + period / years * 392} y="78" textAnchor="middle" className="fill-ink-500 text-xs">{years <= 5 || period === 0 || period === years || period === Math.round(years / 2) ? start + period : ''}</text></g>)}
        {markers.map((marker, i) => <g key={marker.period}><circle cx={24 + marker.period / years * 392} cy="40" r="7" className="fill-brand-800"><title>{marker.label} · Año {start + marker.period}</title></circle><text x={24 + marker.period / years * 392} y={i === 0 ? 18 : 60} textAnchor={marker.period < years * 0.2 ? 'start' : marker.period > years * 0.8 ? 'end' : 'middle'} className="fill-ink-700 text-xs">{marker.label}</text></g>)}
      </svg></div>}</div>
    </Card>
  </div>;
}
