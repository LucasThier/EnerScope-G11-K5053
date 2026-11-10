import type { Evaluation, EconomicPeriod, NumericStatistics } from '../types/economics';
import { operationalColumns, operationalDisplay, operationalYear } from './operationalResults';

type Cell = string | number | null | undefined;
export function csv(rows: Cell[][]): string {
  return '\uFEFF' + rows.map(row => row.map(value => {
    if (value == null || (typeof value === 'number' && !Number.isFinite(value))) return '';
    let text = String(value);
    if (typeof value === 'string' && /^[\s]*[=+@-]/.test(text)) text = "'" + text;
    return '"' + text.replace(/"/g, '""') + '"';
  }).join(',')).join('\r\n') + '\r\n';
}
export function downloadCsv(name: string, content: string) {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url;
  link.download = name.replace(/[^a-zA-Z0-9._-]/g, '_') + '.csv';
  document.body.appendChild(link); link.click(); link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
const financialColumns: [keyof EconomicPeriod, string][] = [
 ['taxableIncome','Ingresos gravados'],['deductibleExpenses','Egresos deducibles'],['nonCashExpenses','Gastos sin desembolso'],
 ['resultBeforeTax','Resultado antes de impuestos'],['taxes','Impuestos'],['resultAfterTax','Resultado después de impuestos'],
 ['nonTaxableIncome','Ingresos no gravados'],['nonTaxableExpenses','Egresos no deducibles'],['nonCashAdjustments','Ajustes sin desembolso'],
 ['cashTimingAdjustment','Ajuste de caja'],['cashFlow','Flujo de caja'],['discountedCashFlow','Flujo descontado'],
];
export function economicCsv(e: Evaluation, round?: number) {
 return csv([['Evaluación','Fecha','Ronda','Moneda','VAN','TIR anual (%)','Estado TIR','Recupero simple (período)','Estado recupero simple','Recupero descontado (período)','Estado recupero descontado','Año','Período',...financialColumns.map(c=>c[1])],
 ...(e.snapshot.result?.periods ?? []).map(p=>[e.id,e.createdAt,round,e.snapshot.configuration?.currency,e.snapshot.result?.npv,
 e.snapshot.result?.indicators?.irr.rate == null ? null : e.snapshot.result.indicators.irr.rate * 100,e.snapshot.result?.indicators?.irr.status,
 e.snapshot.result?.indicators?.simplePayback.period,e.snapshot.result?.indicators?.simplePayback.status,
 e.snapshot.result?.indicators?.discountedPayback.period,e.snapshot.result?.indicators?.discountedPayback.status,p.year,p.period,...financialColumns.map(([key])=>p[key])])]);
}
export function operationalCsv(e: Evaluation, nodeId: string, round?: number) {
 const {rows,units}=operationalDisplay(e,nodeId);
 const name=e.snapshot.physicalNodes?.find(n=>n.id===nodeId)?.name ?? nodeId;
 return csv([['Evaluación','Fecha','Ronda','Nodo ID','Nodo','Año','Período',...operationalColumns.map(([key,label])=>label+' ('+units[key]+')')],
 ...rows.map(row=>[e.id,e.createdAt,round,nodeId,name,operationalYear(e,row.period),row.period,...operationalColumns.map(([key])=>row[key])])]);
}
const stats = ['mean','min','max','p10','p50','p90'] as const;
export function summaryCsv(e: Evaluation) {
 const s=e.snapshot.summary;
 const rows: Cell[][]=[['Evaluación','Fecha','Rondas','Sección','Indicador','Nodo ID','Nodo','Año','Período','Unidad','Muestra','Media','Mínimo','Máximo','P10','P50','P90','Valor','Estado']];
 if(!s) return csv(rows);
 const add=(section:string,label:string,unit:string,values?:NumericStatistics,value?:number|null,status?:string,nodeId?:string,period?:number,year?:number)=>{
  rows.push([e.id,e.createdAt,s.rounds,section,label,nodeId,e.snapshot.physicalNodes?.find(n=>n.id===nodeId)?.name,year,period,unit,
   values?.count,...stats.map(k=>values?.[k]),value,status]);
 };
 const currency=e.snapshot.configuration?.currency ?? '';
 add('Rentabilidad','VAN',currency,s.npv);
 add('Rentabilidad','TIR anual','%',{...s.irr,...Object.fromEntries(stats.map(k=>[k,s.irr[k]==null?null:s.irr[k]! * 100]))});
 add('Rentabilidad','VAN positivo','%',undefined,s.positiveNpvProbability==null?null:s.positiveNpvProbability*100);
 for(const [status,count] of Object.entries(s.irrStatuses)) add('Rentabilidad','Estado TIR','rondas',undefined,count,status);
 for(const [label,p] of [['Recupero simple',s.simplePayback],['Recupero descontado',s.discountedPayback]] as const) {
  add(label,'Período de recuperación','períodos',p.recoveryPeriods);
  add(label,'Probabilidad de recuperación','%',undefined,p.recoveredProbability==null?null:p.recoveredProbability*100);
  add(label,'Base aplicable','rondas',undefined,p.denominator);
  add(label,'Vuelve a saldo negativo','rondas',undefined,p.becomesNegativeAgainCount);
  for(const [status,count] of Object.entries(p.statuses)) add(label,'Estado','rondas',undefined,count,status);
 }
 for(const p of s.cashFlows) add('Flujo anual','Flujo de caja',currency,p.values,undefined,undefined,undefined,p.period,p.year);
 for(const m of s.operational) {
  const rawMetrics=stats.map((key,i)=>({nodeId:m.nodeId,period:i+1,input:null,output:null,exported:null,losses:null,operatingHours:null,events:null,[m.metric]:m.values[key]}));
  const display=operationalDisplay({...e,snapshot:{...e.snapshot,rawMetrics}},m.nodeId);
  const values={...m.values};stats.forEach((key,i)=>{values[key]=display.rows[i][m.metric];});
  add('Operación',operationalColumns.find(([key])=>key===m.metric)?.[1] ?? m.metric,display.units[m.metric],values,undefined,undefined,m.nodeId,m.period,e.snapshot.configuration ? e.snapshot.configuration.startYear+m.period : undefined);
 }
 return csv(rows);
}
