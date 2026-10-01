import { money, percent, irrText, paybackText } from '../../utils/economicFormat';
import { useState } from 'react';
import type { EconomicConfiguration, EconomicPeriod, Evaluation } from '../../types/economics';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';

const labels: Record<string, string> = {
  startYear: 'Año de inversión', years: 'Años operativos', currency: 'Moneda', wacc: 'WACC (fracción anual)',
  taxEntities: 'Entidades fiscales', boundary: 'Entidades consolidadas', nodeProfiles: 'Perfiles de nodos',
  adjustments: 'Reglas generales', contracts: 'Contratos', assets: 'Activos', taxTreatments: 'Tratamientos fiscales',
  conversions: 'Conversiones', id: 'Identificador', nodeId: 'Nodo', name: 'Nombre', jurisdiction: 'Régimen',
  taxRate: 'Tasa (fracción)', carryLosses: 'Traslado de pérdidas', openingLoss: 'Pérdidas iniciales',
  lossExpiryYears: 'Vencimiento de pérdidas (años)', lossOffsetLimit: 'Límite de compensación (fracción)',
  taxPaymentLagYears: 'Demora de pago del impuesto (años)', ownership: 'Participaciones', taxEntityId: 'Entidad',
  share: 'Participación (fracción)', rules: 'Reglas', concept: 'Concepto', counterpartyId: 'Contraparte',
  direction: 'Dirección', cashClassification: 'Movimiento de caja', driver: 'Base de cálculo',
  unitValue: 'Importe o tarifa', unit: 'Unidad', validFrom: 'Vigente desde', validTo: 'Vigente hasta',
  occurrences: 'Aplicaciones', recognitionDate: 'Reconocimiento', cashDate: 'Cobro o pago',
  cost: 'Importe', residualValue: 'Valor residual', purchaseDate: 'Compra', paymentDate: 'Pago',
  serviceDate: 'Puesta en servicio', usefulLifeMonths: 'Vida útil (meses)', classification: 'Clasificación',
  deductibleFraction: 'Proporción deducible', metric: 'Métrica', rawUnit: 'Unidad del simulador',
  factor: 'Factor', annualQuantity: 'Cantidad anual explícita', deliveryNodeId: 'Nodo de entrega', revenueRule: 'Regla de ingreso',
};
const values: Record<string, string> = { CASH: 'En efectivo', NON_CASH: 'Sin desembolso', INCOME: 'Ingreso', EXPENSE: 'Egreso',
  FIXED: 'Importe fijo', OUTPUT_VOLUME: 'Volumen producido', EXPORTED_VOLUME: 'Volumen exportado',
  TAXABLE: 'Gravado', DEDUCTIBLE: 'Deducible', CAPITALIZABLE: 'Capitalizable', Hypothetical: 'Hipotético' };
function ReadValue({ value }: { value: unknown }) {
  if (value == null) return <span>No aplica</span>;
  if (typeof value === 'boolean') return <span>{value ? 'Sí' : 'No'}</span>;
  if (Array.isArray(value)) return value.length ? <ol className="space-y-3">{value.map((v, i) => <li className="border-l border-ink-200 pl-3" key={i}><ReadValue value={v} /></li>)}</ol> : <span>Sin elementos</span>;
  if (typeof value === 'object') return <dl className="space-y-2">{Object.entries(value).map(([k,v]) =>
    <div key={k}><dt className="font-medium text-ink-700">{labels[k] ?? k}</dt><dd className="break-words pl-3 text-ink-500"><ReadValue value={v} /></dd></div>)}</dl>;
  return <span>{values[String(value)] ?? String(value)}</span>;
}
export function ConfigurationSummary({ configuration }: { configuration: EconomicConfiguration }) {
  return <div className="space-y-3 text-sm">{Object.entries(configuration).map(([k,v]) => typeof v === 'object' ?
    <details key={k} className="rounded-lg border border-ink-200 p-3"><summary className="cursor-pointer font-medium">{labels[k] ?? k}</summary><div className="mt-3"><ReadValue value={v} /></div></details> :
    <p key={k}><strong>{labels[k] ?? k}: </strong>{String(v)}</p>)}</div>;
}

export function EconomicResults({ evaluation }: { evaluation: Evaluation }) {
  const [breakdown, setBreakdown] = useState(false);
  const result = evaluation.snapshot.result;
  const indicators = result?.indicators;
  const configuration = evaluation.snapshot.configuration;
  const currency = configuration?.currency;
  const periods = result?.periods ?? [];
  const max = Math.max(1, ...periods.map(p => Math.abs(p.cashFlow)));
  const columns: [keyof EconomicPeriod, string][] = breakdown ? [
    ['taxableIncome','Ingresos gravados'], ['deductibleExpenses','Egresos deducibles'], ['nonCashExpenses','Gastos sin desembolso'],
    ['resultBeforeTax','Resultado antes de impuestos'], ['taxes','Impuestos devengados'], ['resultAfterTax','Resultado después de impuestos'],
    ['nonTaxableIncome','Ingresos no gravados'], ['nonTaxableExpenses','Egresos no deducibles'], ['nonCashAdjustments','Ajustes sin desembolso'],
    ['cashTimingAdjustment','Ajuste de caja'], ['cashFlow','Flujo de caja'], ['discountedCashFlow','Flujo descontado'],
  ] : [['taxableIncome','Ingresos gravados'], ['deductibleExpenses','Costos deducibles'], ['taxes','Impuestos'], ['cashFlow','Flujo de caja'], ['discountedCashFlow','Descontado']];
  return <div className="space-y-5">
    <p className="text-sm text-ink-500">Moneda: {configuration?.currency ?? 'No disponible'} · WACC: {percent(configuration?.wacc)}</p>
    <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">{[
      ['VAN', money(result?.npv, currency)], ['TIR anual', irrText(indicators?.irr)],
      ['Recupero simple', paybackText(indicators?.simplePayback)], ['Recupero descontado', paybackText(indicators?.discountedPayback)],
    ].map(([label,value]) => <Card key={label}><h2 className="text-sm font-medium text-ink-500">{label}</h2><p className="mt-3 text-lg font-semibold text-ink-800">{value}</p></Card>)}</div>
    {indicators?.origin === 'DERIVED_FROM_SNAPSHOT' && <Alert>Indicadores derivados de los flujos históricos guardados; no se ejecutó una nueva simulación.</Alert>}
    {(indicators?.simplePayback?.becomesNegativeAgain || indicators?.discountedPayback?.becomesNegativeAgain) && <Alert>Después del primer recupero, el acumulado vuelve a ser negativo dentro del horizonte. La fecha de primer recupero se conserva.</Alert>}
    {!!result?.pendingBalances?.length && <Alert>Esta evaluación tiene saldos pendientes fuera del horizonte. No se supone su cobro o pago al finalizar.</Alert>}
    <Card><h2 className="mb-4 text-lg font-semibold">Flujo de caja anual</h2>
      {!periods.length ? <p>No hay flujos anuales disponibles.</p> : <div className="overflow-x-auto"><svg className="h-60 min-w-full" width={Math.max(640, periods.length * 60)} height="240" role="img" aria-label="Flujos anuales: barras hacia arriba positivas y hacia abajo negativas. Valores exactos en la tabla siguiente.">
        <line x1="0" x2="100%" y1="110" y2="110" stroke="currentColor" className="text-ink-300" />
        {periods.map((p, i) => { const h = Math.abs(p.cashFlow) / max * 90; return <g key={p.period}><rect x={((i + 0.5) / periods.length * 100) + '%'} y={p.cashFlow >= 0 ? 110 - h : 110} width="30" height={Math.max(h, 1)} className={p.cashFlow >= 0 ? 'fill-ink-700' : 'fill-ink-400'}><title>{p.year}: {money(p.cashFlow,currency)}</title></rect><text x={((i + 0.5) / periods.length * 100) + '%'} y="226" textAnchor="start" className="fill-ink-600 text-xs">{p.year}</text></g>; })}
      </svg></div>}
    </Card>
    <Card><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Flujos anuales · {currency ?? 'Moneda no disponible'}</h2><button className="text-sm font-semibold text-brand-800 underline" onClick={() => setBreakdown(!breakdown)}>{breakdown ? 'Ver resumen' : 'Ver desglose contable'}</button></div>
      <div className="overflow-x-auto"><table className="w-full text-right text-sm"><thead><tr><th className="p-3 text-left">Año / período</th>{columns.map(([key,label]) => <th className="p-3" key={key}>{label}</th>)}</tr></thead><tbody>{periods.map(p => <tr key={p.period} className="border-t border-ink-100"><th className="whitespace-nowrap p-3 text-left">{p.year} / {p.period === 0 ? 'Inicial' : p.period}</th>{columns.map(([key]) => <td className="whitespace-nowrap p-3 tabular-nums" key={key}>{money(p[key],currency)}</td>)}</tr>)}</tbody></table></div>
      <p className="mt-3 text-xs text-ink-500">Los períodos de recupero corresponden al cierre anual, sin interpolación. El redondeo de los flujos descontados puede diferir del VAN en un centavo.</p>
    </Card>
    <Card><details><summary className="cursor-pointer font-semibold">Ver configuración utilizada</summary><div className="mt-4">{configuration ? <ConfigurationSummary configuration={configuration} /> : <p>No hay configuración guardada disponible.</p>}</div></details></Card>
  </div>;
}
