import { money, percent, dateTime } from '../../utils/economicFormat';
import { useState } from 'react';
import type { EconomicConfiguration, EconomicPeriod, Evaluation } from '../../types/economics';
import { Alert } from '../ui/Alert';
import { EconomicSummary } from './EconomicSummary';
import { CashFlowChart } from './CashFlowChart';
import { downloadCsv, economicCsv } from '../../utils/resultsCsv';
import { Button } from '../ui/Button';
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

export function EconomicResults({ evaluation, active = true, round }: { evaluation: Evaluation; active?: boolean; round?: number }) {
  const [breakdown, setBreakdown] = useState(false);
  const result = evaluation.snapshot.result;
  const indicators = result?.indicators;
  const configuration = evaluation.snapshot.configuration;
  const currency = configuration?.currency;
  const periods = result?.periods ?? [];
  const columns: [keyof EconomicPeriod, string][] = breakdown ? [
    ['taxableIncome','Ingresos gravados'], ['deductibleExpenses','Egresos deducibles'], ['nonCashExpenses','Gastos sin desembolso'],
    ['resultBeforeTax','Resultado antes de impuestos'], ['taxes','Impuestos devengados'], ['resultAfterTax','Resultado después de impuestos'],
    ['nonTaxableIncome','Ingresos no gravados'], ['nonTaxableExpenses','Egresos no deducibles'], ['nonCashAdjustments','Ajustes sin desembolso'],
    ['cashTimingAdjustment','Ajuste de caja'], ['cashFlow','Flujo de caja'], ['discountedCashFlow','Flujo descontado'],
  ] : [['taxableIncome','Ingresos gravados'], ['deductibleExpenses','Costos deducibles'], ['taxes','Impuestos'], ['cashFlow','Flujo de caja'], ['discountedCashFlow','Descontado']];
  return <div className="space-y-5">
    <EconomicSummary npv={result?.npv} indicators={indicators} configuration={configuration} heading={<div className="flex flex-wrap items-start justify-between gap-4"><div>
      <h2 className="text-lg font-semibold text-ink-800">Resultados del escenario</h2>
      <p className="mt-2 text-sm text-ink-500">Evaluación: {dateTime(evaluation.createdAt)}</p>
      </div><div className="flex justify-start"><p className="rounded-full border border-ink-200 bg-white px-4 py-2 text-sm text-ink-600">{configuration?.currency ?? 'Moneda no disponible'} · WACC {percent(configuration?.wacc)} · Horizonte {configuration?.years ?? '—'} años</p></div>
    </div>} />
    {indicators?.origin === 'DERIVED_FROM_SNAPSHOT' && <Alert>Indicadores derivados de los flujos históricos guardados; no se ejecutó una nueva simulación.</Alert>}
    {(indicators?.simplePayback?.becomesNegativeAgain || indicators?.discountedPayback?.becomesNegativeAgain) && <Alert>Después del primer recupero, el acumulado vuelve a ser negativo dentro del horizonte. La fecha de primer recupero se conserva.</Alert>}
    {!!result?.pendingBalances?.length && <Alert>Esta evaluación tiene saldos pendientes fuera del horizonte. No se supone su cobro o pago al finalizar.</Alert>}
    <Card><div className="mb-5 flex flex-wrap items-start justify-between gap-3"><div><h2 className="text-xl font-semibold">Flujo de caja anual</h2><p className="mt-1 text-sm text-ink-500">Inversión inicial y {configuration?.years ?? '—'} años operativos · {currency ?? 'Moneda no disponible'}</p></div><span className="flex items-center gap-2 text-xs text-ink-500"><span className="h-2 w-2 rounded-full bg-brand-800" />Flujo de caja</span></div>
      {!periods.length ? <p>No hay flujos anuales disponibles.</p> : <CashFlowChart key={evaluation.id} periods={periods} currency={currency} active={active} />}
    </Card>
    <Card className="relative"><div className="absolute right-6 top-6"><Button variant="secondary" disabled={!periods.length} onClick={() => downloadCsv('resultados-economicos-' + evaluation.id + (round ? '-ronda-' + round : ''), economicCsv(evaluation, round))}>Descargar CSV</Button></div><details><summary className="min-h-10 cursor-pointer py-2 pr-36 font-semibold leading-6 text-ink-800">Ver detalle anual</summary><div className="mt-5"><div className="mb-4 flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-semibold">Flujos anuales · {currency ?? 'Moneda no disponible'}</h2><button className="text-sm font-semibold text-brand-800 underline" onClick={() => setBreakdown(!breakdown)}>{breakdown ? 'Ver resumen' : 'Ver desglose contable'}</button></div>
      <div className="overflow-x-auto"><table className="w-full text-right text-sm"><thead><tr><th className="p-3 text-left">Año / período</th>{columns.map(([key,label]) => <th className="p-3" key={key}>{label}</th>)}</tr></thead><tbody>{periods.map(p => <tr key={p.period} className="border-t border-ink-100"><th className="whitespace-nowrap p-3 text-left">{p.year} / {p.period === 0 ? 'Inicial' : p.period}</th>{columns.map(([key]) => <td className="whitespace-nowrap p-3 tabular-nums" key={key}>{money(p[key],currency)}</td>)}</tr>)}</tbody></table></div>
      <p className="mt-3 text-xs text-ink-500">Los períodos de recupero corresponden al cierre anual, sin interpolación. El VAN se muestra redondeado hacia arriba a la unidad; los flujos conservan sus decimales.</p></div></details>
    </Card>
    <Card><details><summary className="cursor-pointer font-semibold">Ver configuración utilizada</summary><div className="mt-4">{configuration ? <ConfigurationSummary configuration={configuration} /> : <p>No hay configuración guardada disponible.</p>}</div></details></Card>
  </div>;
}
