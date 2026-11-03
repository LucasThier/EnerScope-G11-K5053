import { useRef, useState } from 'react';
import { Button } from '../ui/Button';
import { Alert } from '../ui/Alert';
import { scenarioConfigurationApi } from '../../api/scenarioConfiguration';
import { getErrorMessage } from '../../api/errors';
import { downloadCsv } from '../../utils/resultsCsv';
import { emptyDraft, validateDraft } from '../../utils/economicDraft';
import { automaticNodeMapping, configurationChanges, configurationCsv, ConfigurationCsvError, importConfiguration, MAX_CSV_BYTES, readConfigurationCsv, type ConfigurationFile } from '../../utils/scenarioConfigurationCsv';
import type { OperationalNode, ScenarioSave } from '../../types/scenarioConfiguration';

export function ScenarioCsvImport({projectId,versionId,form,nodes,disabled,dirty,onApply}:{
 projectId:string;versionId:string;form:ScenarioSave;nodes:OperationalNode[];disabled:boolean;dirty:boolean;onApply:(form:ScenarioSave)=>void;
}) {
 const input=useRef<HTMLInputElement>(null),request=useRef(0);
 const [file,setFile]=useState<ConfigurationFile|null>(null),[mapping,setMapping]=useState<Record<string,string>>({});
 const [errors,setErrors]=useState<string[]>([]),[notice,setNotice]=useState(''),[busy,setBusy]=useState(false);
 const [preview,setPreview]=useState<{form:ScenarioSave;signature:string}|null>(null);
 const signature=JSON.stringify({form,nodes,mapping,disabled});
 const currentSignature=useRef(signature);currentSignature.current=signature;
 const simple=form.editor.mode==='simple';
 const candidate=preview?.signature===signature?preview.form:null;
 const changes=candidate?configurationChanges(form,candidate,nodes):[];
 function cancel() {request.current++;setFile(null);setPreview(null);setErrors([]);setBusy(false);if(input.current)input.current.value='';}
 async function selectFile(selected:File|undefined) {
  if(!selected)return;
  const run=++request.current;setPreview(null);setFile(null);setErrors([]);setNotice('');setBusy(true);
  try {
   if(selected.size>MAX_CSV_BYTES)throw new Error('El archivo supera el límite de 2 MB.');
   if(!selected.name.toLowerCase().endsWith('.csv'))throw new Error('Seleccioná un archivo .csv.');
   const text=new TextDecoder('utf-8',{fatal:true}).decode(await selected.arrayBuffer());
   const parsed=readConfigurationCsv(text);
   if(run!==request.current)return;
   setFile(parsed);setMapping(automaticNodeMapping(parsed,nodes));
  }catch(e){if(run===request.current)setErrors(e instanceof ConfigurationCsvError?e.issues:[getErrorMessage(e,'No se pudo leer el CSV. Guardalo como CSV UTF-8.')]);}
  finally{if(run===request.current)setBusy(false);if(input.current)input.current.value='';}
 }
 async function validate() {
  if(!file||disabled||busy)return;
  const run=++request.current,baseline=signature;setBusy(true);setErrors([]);setPreview(null);
  try {
   const next=importConfiguration(file,nodes,mapping,form);
   const result=await scenarioConfigurationApi.validate(projectId,versionId,next);
   if(run!==request.current)return;
   if(currentSignature.current!==baseline)throw new Error('La configuración cambió durante la validación. Volvé a validar el archivo.');
   if(result.errors.length) {
    setErrors(result.errors.map(issue=>{
     const source=Object.entries(mapping).find(([,id])=>id===issue.nodeId)?.[0];
     const rows=file.rows.filter(r=>source?r.section==='Operacion'&&r.node===source&&(r.field===issue.field||r.field.startsWith(issue.field+'.')):r.section!=='Nodos'&&r.section!=='Metadatos');
     return `${source?'Operación':'Configuración'} / ${issue.field}${rows.length?' · filas '+rows.map(r=>r.line).join(', '):''}: ${issue.message}`;
    }));return;
   }
   setPreview({form:next,signature:baseline});
  }catch(e){if(run===request.current)setErrors(e instanceof ConfigurationCsvError?e.issues:[getErrorMessage(e,'No se pudo validar la configuración.')]);}
  finally{if(run===request.current)setBusy(false);}
 }
 function exportFile(template:boolean) {
  try {
   const output=template?{...form,editor:{mode:'simple' as const,value:emptyDraft()}}:form;
   const text=configurationCsv(output,nodes);
   if(new TextEncoder().encode(text).length>MAX_CSV_BYTES)throw new Error('La configuración supera el límite CSV de 2 MB.');
   downloadCsv(`${template?'plantilla':'configuracion'}-escenario-${versionId}`,text);
   const pending=form.editor.mode==='simple'&&Object.values(validateDraft(form.editor.value,nodes)).some(errors=>errors.length);
   setNotice(template?'Plantilla descargada. Completá Economía y revisá los valores operativos incluidos antes de importar.':pending?'Archivo exportado con datos pendientes. Completalos antes de importarlo.':'Configuración del formulario exportada, incluidos los cambios sin guardar.');
  }catch(e){setErrors([getErrorMessage(e,'No se pudo exportar.')]);}
 }
 function apply() {
  if(!candidate||disabled)return;
  if(dirty&&!window.confirm('Se reemplazarán los cambios del formulario por la configuración del CSV. ¿Continuar?'))return;
  onApply(candidate);cancel();setNotice('CSV cargado en el formulario. Revisá los valores y pulsá Guardar para persistirlos.');
 }
 return <section aria-label="Importación de configuración CSV" className="space-y-3 rounded-xl border border-ink-200 bg-white p-4">
 <div className="flex flex-wrap items-center gap-2">
 <Button variant="secondary" disabled={disabled||!simple||busy} onClick={()=>input.current?.click()}>Importar CSV</Button>
 <Button variant="ghost" disabled={disabled||!simple||busy} onClick={()=>exportFile(true)}>Descargar plantilla</Button>
 <Button variant="ghost" disabled={!simple||busy} onClick={()=>exportFile(false)}>Exportar configuración CSV</Button>
 <a className="text-sm text-brand-800 underline" href="/scenario-configuration-csv.md" download>Instrucciones y ejemplos</a>
 <input ref={input} aria-label="Archivo CSV de configuración" className="sr-only" type="file" accept=".csv,text/csv" disabled={disabled||!simple||busy} onChange={e=>void selectFile(e.target.files?.[0])}/>
 </div>
 {!simple&&<p className="text-sm text-ink-500">Esta configuración contiene reglas avanzadas. Creá una copia simple con la opción de abajo para utilizar CSV.</p>}
 {notice&&<p role="status" className="text-sm">{notice}</p>}
 {errors.length>0&&<Alert tone="error"><ul className="max-h-64 list-disc overflow-auto pl-5">{errors.map((e,i)=><li key={i}>{e}</li>)}</ul></Alert>}
 {busy&&<p role="status">Leyendo o validando configuración…</p>}
 {file&&<div className="space-y-4">
 <h2 className="font-semibold">Vincular nodos del archivo</h2>
 <p className="text-sm">Cada nodo debe corresponder a un nodo del mismo tipo. Se reemplazarán Economía y Operación completas; el mapa conserva sus conexiones.</p>
 <div className="grid gap-3 sm:grid-cols-2">{file.nodes.map(source=><label className="flex min-w-0 flex-col gap-1 text-sm" key={source.key}>
 {source.name} · {source.type}
 <select className="min-w-0 rounded-lg border border-ink-200 p-2" disabled={disabled||busy} value={mapping[source.key]??''} onChange={e=>{setMapping({...mapping,[source.key]:e.target.value});setPreview(null);setErrors([]);}}>
 <option value="">Seleccionar nodo del escenario</option>
 {nodes.filter(n=>n.type===source.type).map(n=><option key={n.id} value={n.id}>{n.name} · {n.id.slice(0,8)}</option>)}
 </select></label>)}</div>
 <div className="flex gap-2"><Button disabled={disabled||busy} onClick={()=>void validate()}>Validar CSV</Button><Button variant="ghost" onClick={cancel}>Cancelar importación</Button></div>
 {candidate&&<div className="space-y-3">
 <h2 className="font-semibold">Vista previa · {changes.length} cambios</h2>
 <p className="text-sm">Las inversiones y reglas ausentes del archivo se quitarán de la configuración actual. Las evaluaciones anteriores se conservan.</p>
 <div className="max-h-96 overflow-auto"><table className="w-full text-left text-sm"><thead><tr><th className="p-2">Campo</th><th className="p-2">Actual</th><th className="p-2">CSV</th></tr></thead>
 <tbody>{changes.map(c=><tr key={c.field} className="border-t border-ink-100"><th className="p-2 font-normal">{c.field}</th><td className="break-all p-2">{c.before}</td><td className="break-all p-2">{c.after}</td></tr>)}</tbody></table></div>
 <Button disabled={disabled||busy} onClick={apply}>Cargar en el formulario</Button>
 </div>}
 {preview&&!candidate&&<p role="status">La configuración cambió. Volvé a validar antes de cargar el CSV.</p>}
 </div>}
 </section>;
}
