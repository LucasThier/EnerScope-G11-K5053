import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { NumericStatistics } from '../../types/economics';
import { bandSegments, numericDomain, validBand, type BandPoint } from '../../utils/summaryVisuals';
import { Card } from '../ui/Card';

type Formatter = (value: number | null) => string;
export function SummaryMetric({label,value,caption}:{label:string;value:string;caption:string}) {
 return <Card><h3 className="text-sm font-medium text-ink-500">{label}</h3><p className="my-3 break-words text-2xl font-semibold text-ink-800">{value}</p><p className="text-xs text-ink-500">{caption}</p></Card>;
}
export function StatisticsDetails({children}:{children:ReactNode}) {
 return <details className="mt-4 border-t border-ink-100 pt-3"><summary className="cursor-pointer rounded text-sm font-medium text-brand-800 focus-visible:outline-2 focus-visible:outline-brand-500">Ver detalle estadístico</summary><div className="mt-3">{children}</div></details>;
}
export function PercentileRange({label,values,format}:{label:string;values:NumericStatistics;format:Formatter}) {
 if (!validBand(values)) return <div><h3 className="font-semibold">{label}</h3><p className="text-sm text-ink-500">No disponible · {values.count} observaciones</p></div>;
 const low=values.p10!,middle=values.p50!,high=values.p90!;
 const [min,max]=numericDomain([low,high],false);
 const x=(value:number)=>28+(value-min)/(max-min)*544;
 return <div className="min-w-0"><h3 className="font-semibold">{label}</h3>
 <svg viewBox="0 0 600 70" className="w-full" role="img" aria-label={`${label}: P10 ${format(low)}, P50 ${format(middle)}, P90 ${format(high)}`}>
  <line x1="28" x2="572" y1="35" y2="35" stroke="#cbd5e1" strokeWidth="2"/>
  {min<=0&&max>=0&&<g><line x1={x(0)} x2={x(0)} y1="10" y2="60" stroke="#64748b" strokeDasharray="3 3"/><text x={x(0)} y="9" textAnchor="middle" fontSize="11">0</text></g>}
  <line x1={x(low)} x2={x(high)} y1="35" y2="35" stroke="#bbdfc0" strokeWidth="14" strokeLinecap="round"/>
  <circle cx={x(middle)} cy="35" r="7" fill="#287b31" stroke="white" strokeWidth="2"/>
 </svg>
 <dl className="grid grid-cols-3 gap-2 text-xs">{[['P10',low],['P50 · mediana',middle],['P90',high]].map(([name,value])=><div key={name}><dt className="text-ink-500">{name}</dt><dd className="mt-1 break-words font-semibold">{format(value as number)}</dd></div>)}</dl>
 {values.min===values.max&&values.min!==null&&<p className="mt-2 text-xs text-ink-500">Sin dispersión observada</p>}
 <p className="mt-2 text-xs text-ink-500">Muestra: {values.count} rondas</p>
 </div>;
}
export function RecoveryBar({label,value}:{label:string;value:number|null}) {
 return value===null ? <p className="text-sm text-ink-500">No disponible</p> : <div role="meter" aria-label={label} aria-valuemin={0} aria-valuemax={100} aria-valuenow={value*100} className="my-3 h-3 overflow-hidden rounded-full bg-ink-100"><div className="h-full rounded-full bg-brand-600" style={{width:`${value*100}%`}}/></div>;
}
export function AnnualBandChart({points,label,format,unit}:{points:BandPoint[];label:string;format:Formatter;unit:string}) {
 const [active,setActive]=useState<number|null>(null);
 const container=useRef<HTMLDivElement>(null);
 const [containerWidth,setContainerWidth]=useState(0);
 useEffect(()=>{
  const element=container.current;if(!element)return;
  const observer=new ResizeObserver(entries=>setContainerWidth(entries[0]?.contentRect.width??0));
  observer.observe(element);return ()=>observer.disconnect();
 },[]);
 const sorted=[...points].sort((a,b)=>a.period-b.period),segments=bandSegments(sorted);
 if (!segments.length) return <div ref={container}><p className="my-5 text-sm text-ink-500">No hay datos disponibles para este gráfico.</p></div>;
 const available=segments.flat();
 const [low,high]=numericDomain(available.flatMap(p=>[p.values.p10,p.values.p90]));
 const first=sorted[0].period,last=sorted[sorted.length-1].period;
 const width=Math.max(660,containerWidth,sorted.length*64+130),height=260;
 const x=(p:BandPoint)=>110+(p.period-first)/Math.max(1,last-first)*(width-140);
 const y=(v:number)=>215-(v-low)/(high-low)*175;
 const selected=available.find(p=>p.period===active);
 const noSpread=available.every(p=>p.values.min!==null&&p.values.min===p.values.max);
 return <div ref={container} className="mt-4">
  <p className="text-xs text-ink-500">{unit} · Línea: P50 (mediana) · Banda: P10–P90</p>
  <p className="mt-1 text-xs text-ink-500 sm:hidden">Deslizá el gráfico para ver todos los años.</p>
  <div className="mt-2 overflow-x-auto"><svg width={width} height={height} className="min-w-full" role="group" aria-label={label}>
   {[low,(low+high)/2,high].map((v,i)=><g key={i}><line x1="110" x2={width-30} y1={y(v)} y2={y(v)} stroke="#e2e8f0"/><text x="100" y={y(v)+4} textAnchor="end" fontSize="10" fill="#475569">{new Intl.NumberFormat('es-AR',{notation:'compact',maximumFractionDigits:1}).format(v)}</text></g>)}
   <line x1="110" x2={width-30} y1={y(0)} y2={y(0)} stroke="#64748b" strokeDasharray="4 4"/>
   <text x={width-25} y={y(0)+4} fontSize="10" fill="#475569">0</text>
   {segments.map((segment,i)=><g key={i}>
    <polygon points={[...segment.map(p=>`${x(p)},${y(p.values.p90!)}`),...segment.slice().reverse().map(p=>`${x(p)},${y(p.values.p10!)}`)].join(' ')} fill="#deefdf"/>
    <polyline points={segment.map(p=>`${x(p)},${y(p.values.p50!)}`).join(' ')} fill="none" stroke="#287b31" strokeWidth="2.5"/>
   </g>)}
   {sorted.map(p=><text key={p.period} x={x(p)} y="244" textAnchor="middle" fontSize="11" fill="#475569">{p.label}</text>)}
   {available.map(p=><g key={p.period} tabIndex={0} role="button" aria-label={`${p.label}: P10 ${format(p.values.p10)}, P50 ${format(p.values.p50)}, P90 ${format(p.values.p90)}, muestra ${p.values.count}`} className="group cursor-help focus:outline-none" onFocus={()=>setActive(p.period)} onBlur={()=>setActive(null)} onMouseEnter={()=>setActive(p.period)} onMouseLeave={()=>setActive(null)} onClick={()=>setActive(p.period)} onKeyDown={e=>{if(e.key==='Escape')setActive(null);if(e.key==='Enter'||e.key===' '){e.preventDefault();setActive(p.period);}}}>
    <line x1={x(p)} x2={x(p)} y1={y(p.values.p10!)} y2={y(p.values.p90!)} stroke="#75ad7b" strokeWidth="3"/>
    <circle className="group-focus:stroke-ink-800 group-focus:stroke-[3px]" cx={x(p)} cy={y(p.values.p50!)} r={active===p.period?7:4} fill="#287b31" stroke={active===p.period?'#172c1b':'white'} strokeWidth="2"/>
    <circle cx={x(p)} cy={y(p.values.p50!)} r="14" fill="transparent"/>
   </g>)}
  </svg></div>
  <div className="min-h-12 rounded-lg bg-ink-50 p-3 text-xs text-ink-600" aria-live="polite">{selected ? `${selected.label} · P10: ${format(selected.values.p10)} · P50: ${format(selected.values.p50)} · P90: ${format(selected.values.p90)} · Muestra: ${selected.values.count} rondas` : 'Pasá el cursor o enfocá un punto con el teclado para consultar sus valores.'}</div>
  {noSpread&&<p className="mt-2 text-xs text-ink-500">Sin dispersión observada</p>}
 </div>;
}
