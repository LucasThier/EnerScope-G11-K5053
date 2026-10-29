import { useState } from 'react';
import { Card } from '../ui/Card';
import { TextField } from '../ui/TextField';
import type { OperationalNode, OperationalValue, ScenarioSave } from '../../types/scenarioConfiguration';
const labels: Record<string,string> = {
 consumptionSummer:'Consumo de verano', consumptionAutumn:'Consumo de otoño',
 consumptionWinter:'Consumo de invierno', consumptionSpring:'Consumo de primavera', consumption:'Consumo constante',
 name:'Nombre', state:'Estado', startupDate:'Puesta en marcha (UTC, formato ISO)',
 lifespanInMonths:'Vida útil (meses)', maintenanceIntervalInDays:'Intervalo de mantenimiento (días)',
 maintenanceDuration:'Duración del mantenimiento (horas)', wastePercentage:'Pérdidas (%)',
 maxCollectionCapacity:'Capacidad máxima de recolección', declineCurve:'Declinación anual (puntos porcentuales)',
 gasRichness:'Riqueza del gas (0–1)', DTMTime:'Tiempo DTM', surface:'Superficie',
 maxTransportCapacity:'Capacidad máxima de transporte', length:'Longitud', lossPerMeter:'Pérdidas por metro',
 connectedWells:'Pozos conectados', maxTreatmentCapacity:'Capacidad de tratamiento',
 contaminantWaste:'Residuos contaminantes', intermediateStorage:'Almacenamiento intermedio',
 maxFlowCapacity:'Capacidad máxima de flujo', lossPerKm:'Pérdidas por kilómetro',
 maxCompressionCapacity:'Capacidad de compresión', processWaste:'Pérdidas del proceso',
 gasConsumption:'Consumo de gas', maxProcessingCapacity:'Capacidad de procesamiento', mtpaRatio:'Relación MTPA',
 vesselDepth:'Calado', portDepth:'Profundidad del puerto', shipCapacity:'Capacidad del buque',
 exportFrequency:'Frecuencia de exportación', fullLoadTime:'Tiempo de carga completa', timeToDestination:'Tiempo al destino',
};
const selectClass='rounded-lg border border-ink-200 bg-white p-2 text-sm';
export function ScenarioOperationsEditor({ nodes, values, onChange, disabled }: {
 nodes: OperationalNode[]; values: ScenarioSave['operations']; onChange: (values: ScenarioSave['operations'])=>void; disabled: boolean;
}) {
 const [selected,setSelected]=useState(nodes[0]?.id ?? '');
 const node=nodes.find(n=>n.id===selected) ?? nodes[0];
 const change=(key:string,value:OperationalValue)=>onChange({...values,[node.id]:{...values[node.id],[key]:value}});
 return <Card><h2 className="text-lg font-semibold">Operación</h2>
 <p className="my-3 text-sm text-ink-500">Los cambios guardados desde el mapa se reflejan aquí. Los valores físicos conservan las unidades del modelo.</p>
 {!node ? <p>Este escenario no tiene nodos. Agregalos desde el mapa de la cadena de valor.</p> : <>
 <label className="mb-5 flex flex-col gap-2 text-sm">Nodo<select className={selectClass} value={node.id} onChange={e=>setSelected(e.target.value)}>
 {nodes.map(n=><option key={n.id} value={n.id}>{n.name} · {n.type}</option>)}</select></label>
 <fieldset disabled={disabled} className="grid gap-4 sm:grid-cols-2 disabled:opacity-60">
 {node.fields.map(field=>{
 const value=values[node.id]?.[field.name] ?? null, label=labels[field.name] ?? field.name;
 if(field.type==='distribution') {
 const d=value && typeof value==='object' ? value : {type:'CONSTANT',a:''};
 return <fieldset className="space-y-3 rounded-lg border border-ink-200 p-4 sm:col-span-2" key={field.name}><legend>{label}</legend>
 <label className="flex flex-col gap-2 text-sm">Distribución<select className={selectClass} value={d.type} onChange={e=>change(field.name,{type:e.target.value,a:d.a,...(e.target.value==='CONSTANT'?{}:{b:d.b ?? ''})})}>
 <option value="CONSTANT">Constante</option><option value="NORMAL">Normal</option><option value="UNIFORM">Uniforme</option></select></label>
 <TextField label={d.type==='NORMAL'?'Media':d.type==='UNIFORM'?'Mínimo':'Valor constante'} value={d.a} onChange={e=>change(field.name,{...d,a:e.target.value})}/>
 {d.type!=='CONSTANT' && <TextField label={d.type==='NORMAL'?'Desviación estándar':'Máximo'} value={d.b ?? ''} onChange={e=>change(field.name,{...d,b:e.target.value})}/>}
 </fieldset>;
 }
 if(field.type==='state') return <label key={field.name} className="flex flex-col gap-2 text-sm">{label}<select className={selectClass} value={String(value ?? '')} onChange={e=>change(field.name,e.target.value)}>
 {(value==='REMOVED'?['REMOVED','PROPOSED','PENDING','RUNNING']:['PROPOSED','PENDING','RUNNING']).map(s=><option key={s}>{s}</option>)}</select></label>;
 return <TextField key={field.name} label={label} value={typeof value==='object'?'':value ?? ''} inputMode={['number','integer'].includes(field.type)?'decimal':undefined} onChange={e=>change(field.name,e.target.value)}/>;
 })}
 </fieldset></>}
 </Card>;
}
