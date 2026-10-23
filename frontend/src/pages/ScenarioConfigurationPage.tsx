import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { economicsApi } from '../api/economics';
import { projectsApi } from '../api/projects';
import { scenarioConfigurationApi as api } from '../api/scenarioConfiguration';
import { getErrorMessage } from '../api/errors';
import { initialScenario, prepareScenario, scenarioConflict } from '../utils/scenarioConfiguration';
import type { ScenarioConfiguration, ScenarioSave } from '../types/scenarioConfiguration';
import type { Scenario } from '../types/economics';
import { EconomicEditor } from '../components/economics/EconomicEditor';
import { createSimpleCopyDraft } from '../utils/economicDraft';
import { ScenarioOperationsEditor } from '../components/economics/ScenarioOperationsEditor';
import { Button } from '../components/ui/Button';
import { Alert } from '../components/ui/Alert';
export function ScenarioConfigurationPage() {
 const {projectId='',versionId=''}=useParams();
 return <Workspace key={projectId+versionId} projectId={projectId} versionId={versionId}/>;
}
function Workspace({projectId,versionId}:{projectId:string;versionId:string}) {
 const {user}=useAuth();
 const navigate=useNavigate();
 const [snapshot,setSnapshot]=useState<ScenarioConfiguration|null>(null),[form,setForm]=useState<ScenarioSave|null>(null);
 const [baseline,setBaseline]=useState(''),[scenario,setScenario]=useState<Scenario|null>(null),[canEdit,setCanEdit]=useState(false);
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[conflict,setConflict]=useState(false);
 const [operationTab,setOperationTab]=useState(false);
 const latest=useRef({snapshot,form,baseline}),inFlight=useRef(false);
 useEffect(()=>{latest.current={snapshot,form,baseline};},[snapshot,form,baseline]);
 const navigatingCopy=useRef(false);
 const dirty=!!form && JSON.stringify(form)!==baseline;
 const blocker=useBlocker(({currentLocation,nextLocation})=>!navigatingCopy.current&&(dirty||busy)&&currentLocation.pathname!==nextLocation.pathname);
 const hydrate=useCallback((state:ScenarioConfiguration)=>{
  const initial=initialScenario(state);setSnapshot(state);setForm(initial);setBaseline(JSON.stringify(initial));
  setConflict(!!state.draft && state.draft.sourceToken!==state.sourceToken);
  setNotice(state.draft || state.legacyDraft ? 'Borrador recuperado. Completá o descartá los cambios para habilitar la simulación.' : '');
 },[]);
 useEffect(()=>{
  let cancelled=false;
  Promise.all([api.get(projectId,versionId),economicsApi.scenarios(projectId),user?.platformRole==='ADMIN'?Promise.resolve(null):projectsApi.members(projectId)])
   .then(([state,scenarios,members])=>{
    if(cancelled)return;const selected=scenarios.find(s=>s.id===versionId);if(!selected)throw new Error('Escenario no disponible.');
    setScenario(selected);setCanEdit(user?.platformRole==='ADMIN'||!!members?.data.data?.some(m=>m.userId===user?.id&&m.active&&m.permissions.includes('EDIT_PROJECT')));hydrate(state);
   }).catch(e=>{if(!cancelled)setError(getErrorMessage(e,'No se pudo cargar la configuración.'));});
  return ()=>{cancelled=true;};
 },[projectId,versionId,user?.id,user?.platformRole,hydrate]);
 useEffect(()=>{
  let cancelled=false,checking=false;
  async function refresh() {
   if(cancelled||checking||inFlight.current||document.visibilityState==='hidden'||!latest.current.snapshot)return;
   checking=true;
   try {
    const current=await api.get(projectId,versionId);if(cancelled||inFlight.current)return;
    const old=latest.current;
    if(!old.snapshot)return;
    if(scenarioConflict(current,old.snapshot,JSON.stringify(old.form)!==old.baseline)) setConflict(true);
    else if(current.token!==old.snapshot.token) hydrate(current);
   }catch(e){if(!cancelled)setError(getErrorMessage(e,'No se pudieron actualizar los datos del escenario.'));}
   finally{checking=false;}
  }
  window.addEventListener('focus',refresh);document.addEventListener('visibilitychange',refresh);
  return ()=>{cancelled=true;window.removeEventListener('focus',refresh);document.removeEventListener('visibilitychange',refresh);};
 },[projectId,versionId,hydrate]);
 useEffect(()=>{
  if(!dirty&&!busy)return;const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
  window.addEventListener('beforeunload',warn);return ()=>window.removeEventListener('beforeunload',warn);
 },[dirty,busy]);
 const nodes=snapshot?.nodes.map(n=>({id:n.id,name:n.name,type:n.type})) ?? [];
 async function save() {
  if(!form||!canEdit||inFlight.current||conflict)return;inFlight.current=true;setBusy(true);setError('');
  try {const state=await api.save(projectId,versionId,prepareScenario(form,nodes));hydrate(state);
   setNotice(state.draft?'Borrador guardado. Hay datos pendientes; el escenario todavía no puede simularse.':'Configuración económica y operativa guardada. El escenario está listo para simular.');
  }catch(e){setError(getErrorMessage(e,'No se pudo guardar el escenario.'));if((e as {response?:{status:number}}).response?.status===409)setConflict(true);}
  finally{inFlight.current=false;setBusy(false);}
 }
 async function createSimpleCopy() {
  if(!snapshot?.configuration || !scenario || !canEdit || inFlight.current || dirty || conflict || snapshot.draft || snapshot.legacyDraft)return;
  if(!window.confirm('Crear una copia con una entidad al 100 %, ingresos gravados y costos deducibles. No se trasladan participaciones, quebrantos, transferencias internas ni cobros diferidos. Se conservan los originales y sus resultados. Revisá los importes y vigencias de la copia antes de simular.'))return;
  inFlight.current=true;setBusy(true);setError('');
  let copyId:string|undefined;
  try {
   const created=await projectsApi.createVersion(projectId,{name:(scenario.name+' · copia simple').slice(0,120),parentVersion:versionId});
   copyId=created.data.data?.id;if(!copyId)throw new Error('No se recibió la copia del escenario.');
   const current=await api.get(projectId,copyId);
   const nodeIds=Object.fromEntries(snapshot.nodes.map(n=>[n.id,current.nodes.find(other=>other.identity===n.identity)?.id ?? n.id]));
   const copy=initialScenario(current);copy.editor={mode:'simple',value:createSimpleCopyDraft(snapshot.configuration,nodeIds)};
   await api.save(projectId,copyId,prepareScenario(copy,current.nodes.map(n=>({id:n.id,name:n.name,type:n.type}))));
   navigatingCopy.current=true;
   navigate('/projects/'+projectId+'/versions/'+copyId+'/economics/configuration');
  }catch(e){setError(getErrorMessage(e,'No se pudo completar la copia.')+(copyId?' La copia creada sigue disponible en el listado de configuración.':''));}
  finally{inFlight.current=false;setBusy(false);}
 }
 async function discard() {
  if(!canEdit||inFlight.current||!window.confirm('¿Descartar los cambios locales y el borrador y cargar la configuración publicada actual?'))return;
  inFlight.current=true;setBusy(true);setError('');
  try { const current=await api.get(projectId,versionId);hydrate(await api.discard(projectId,versionId,current.token));setConflict(false); }
  catch(e){setError(getErrorMessage(e,'No se pudieron descartar los cambios.'));}
  finally{inFlight.current=false;setBusy(false);}
 }
 if(!snapshot||!form||!scenario)return error?<Alert tone="error">{error}</Alert>:<p role="status">Cargando configuración…</p>;
 const disabled=!canEdit||conflict;
 return <div className="space-y-5">
 <Link className="text-brand-800 underline" to={'/projects/'+projectId+'/scenario-configurations'}>← Configuración del Escenario / {scenario.name}</Link>
 <header className="flex flex-wrap justify-between gap-3"><h1 className="text-2xl font-semibold">Configuración del Escenario</h1><span role="status">{busy?'Guardando…':dirty?'Cambios sin guardar':'Sin cambios pendientes'}</span></header>
 <p className="text-sm text-ink-500">Guardá la configuración económica y operativa. La ejecución se inicia desde Simulación de Escenarios.</p>
 {error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}</Alert>}
 {snapshot.errors.length>0&&<Alert tone="error"><ul>{snapshot.errors.map((e,i)=><li key={i}>{e}</li>)}</ul></Alert>}
 {conflict&&<Alert tone="error">El escenario cambió desde otra pantalla. Tus cambios se conservan; para evitar sobrescribir datos, recargá descartando los cambios afectados.<Button variant="ghost" disabled={busy} onClick={()=>void discard()}>Descartar y recargar</Button></Alert>}
 {blocker.state==='blocked'&&<Alert>Hay cambios sin guardar.<Button variant="ghost" onClick={()=>blocker.reset()}>Seguir editando</Button><Button variant="ghost" disabled={busy} onClick={()=>blocker.proceed()}>Salir sin guardar</Button></Alert>}
 <nav aria-label="Configuración económica y operativa" className="flex gap-3 border-b border-ink-200">
 <Button variant="ghost" aria-pressed={!operationTab} onClick={()=>setOperationTab(false)}>Economía</Button>
 <Button variant="ghost" aria-pressed={operationTab} onClick={()=>setOperationTab(true)}>Operación</Button></nav>
 {operationTab ? <>
 <div className="flex justify-between gap-3"><Button variant="ghost" disabled={!canEdit||busy} onClick={()=>void discard()}>Descartar cambios</Button><Button disabled={disabled} loading={busy} onClick={()=>void save()}>Guardar</Button></div>
 <ScenarioOperationsEditor nodes={snapshot.nodes} values={form.operations} disabled={disabled||busy} onChange={operations=>setForm({...form,operations})}/>
 <div className="flex justify-end"><Button disabled={disabled} loading={busy} onClick={()=>void save()}>Guardar</Button></div>
 </> : form.editor.mode==='simple' ?
 <EconomicEditor saveOnly draft={form.editor.value} nodes={nodes} disabled={disabled} busy={busy} dirty={dirty||!!snapshot.draft||!!snapshot.legacyDraft} onChange={value=>setForm({...form,editor:{mode:'simple',value}})} save={()=>void save()} discard={()=>void discard()}/> :
 <section className="space-y-4 rounded-xl border border-ink-200 bg-white p-5">
  <h2 className="text-lg font-semibold">Configuración original conservada</h2>
  <p>Este escenario contiene reglas que el formulario simple no representa. Sus datos y resultados se conservan intactos. Podés crear una copia para editarla con el formulario simple.</p>
  <dl className="grid gap-3 sm:grid-cols-2"><div><dt>Año de inversión</dt><dd>{form.editor.value.startYear}</dd></div><div><dt>Años operativos</dt><dd>{form.editor.value.years}</dd></div><div><dt>Moneda</dt><dd>{form.editor.value.currency}</dd></div><div><dt>Entidades fiscales</dt><dd>{form.editor.value.taxEntities.length}</dd></div></dl>
  <p className="text-sm text-ink-500">La copia usa una entidad al 100 %, ingresos gravados y costos deducibles. Debés revisar importes y vigencias: no reproduce participaciones, quebrantos, transferencias internas ni cobros diferidos.</p>
  <Button disabled={disabled||busy||dirty||!!snapshot.draft||!!snapshot.legacyDraft} onClick={()=>void createSimpleCopy()}>Crear copia simple</Button>
  {(snapshot.draft||snapshot.legacyDraft)&&<><p>Hay un borrador conservado. Descartalo explícitamente para crear una copia desde la configuración publicada.</p><Button variant="ghost" disabled={!canEdit||busy} onClick={()=>void discard()}>Descartar cambios</Button></>}
 </section>}
 </div>;
}
