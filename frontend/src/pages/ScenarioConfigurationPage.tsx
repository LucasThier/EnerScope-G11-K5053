import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useBlocker, useNavigate, useParams } from 'react-router-dom';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import { economicsApi } from '../api/economics';
import { scenariosApi } from '../api/scenarios';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import { isProjectAccessLoss } from '../utils/activeProject';
import { initialScenario, prepareScenario, scenarioConflict } from '../utils/scenarioConfiguration';
import { OUTDATED_DETAIL, OUTDATED_NOTICE, IGNORED_OPERATIONS_NOTICE, scenarioPermissions, scenarioStatusView } from '../utils/scenarioRules';
import { scenarioConfigurationPath, scenarioHistoryPath, versionOperationPath, versionScenariosPath } from '../utils/scenarioRoutes';
import type { ScenarioConfiguration, ScenarioSave } from '../types/scenarioConfiguration';
import type { ScenarioSummary } from '../types/scenario';
import { EconomicEditor } from '../components/economics/EconomicEditor';
import { createSimpleCopyDraft } from '../utils/economicDraft';
import { ScenarioNameDialog } from '../components/scenarios/ScenarioDialogs';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { PageHeader } from '../components/ui/PageHeader';
import { Alert } from '../components/ui/Alert';

export function ScenarioConfigurationPage() {
 const {projectId='',versionId='',scenarioId=''}=useParams();
 return <Workspace key={projectId+versionId+scenarioId} projectId={projectId} versionId={versionId} scenarioId={scenarioId}/>;
}

/**
 * The economic configuration of one scenario. Node values ("Operación") belong to the version and are edited on the
 * node, so this screen links to the version's editor instead of editing them.
 */
function Workspace({projectId,versionId,scenarioId}:{projectId:string;versionId:string;scenarioId:string}) {
 const {reload:reloadProjects}=useActiveProject();
 const navigate=useNavigate();
 const [snapshot,setSnapshot]=useState<ScenarioConfiguration|null>(null),[form,setForm]=useState<ScenarioSave|null>(null);
 const [baseline,setBaseline]=useState(''),[scenario,setScenario]=useState<ScenarioSummary|null>(null),[versionName,setVersionName]=useState('');
 const {access,failure:accessFailure,retry:retryAccess}=useGuardedProjectAccess(projectId);
 const permissions=scenarioPermissions(access,versionId);
 const canEdit=permissions.canEdit;
 const [busy,setBusy]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState(''),[conflict,setConflict]=useState(false);
 const [copying,setCopying]=useState(false);
 const latest=useRef({snapshot,form,baseline}),inFlight=useRef(false);
 useEffect(()=>{latest.current={snapshot,form,baseline};},[snapshot,form,baseline]);
 const navigatingAway=useRef(false);
 const dirty=!!form && JSON.stringify(form)!==baseline;
 const blocker=useBlocker(({currentLocation,nextLocation})=>!navigatingAway.current&&(dirty||busy)&&currentLocation.pathname!==nextLocation.pathname);
 const hydrate=useCallback((state:ScenarioConfiguration)=>{
  const initial=initialScenario(state);setSnapshot(state);setForm(initial);setBaseline(JSON.stringify(initial));
  setConflict(!!state.draft && state.draft.sourceToken!==state.sourceToken);
  setNotice(state.draftOperationsIgnored ? IGNORED_OPERATIONS_NOTICE : state.draft || state.legacyDraft ? 'Borrador recuperado. Completá o descartá los cambios para habilitar la simulación.' : '');
 },[]);
 useEffect(()=>{
  let cancelled=false;
  Promise.all([scenariosApi.configuration(projectId,scenarioId),scenariosApi.get(projectId,scenarioId),economicsApi.scenarios(projectId)])
   .then(([state,summary,versions])=>{
    if(cancelled)return;
    if(summary.versionId!==versionId)throw new Error('Escenario no disponible.');
    setScenario(summary);setVersionName(versions.find(v=>v.id===versionId)?.name ?? '');hydrate(state);
   }).catch(e=>{if(cancelled)return;if(isProjectAccessLoss(getErrorStatus(e)))void reloadProjects();setError(getErrorMessage(e,'No se pudo cargar la configuración.'));});
  return ()=>{cancelled=true;};
 },[projectId,versionId,scenarioId,hydrate,reloadProjects]);
 useEffect(()=>{
  let cancelled=false,checking=false;
  async function refresh() {
   if(cancelled||checking||inFlight.current||document.visibilityState==='hidden'||!latest.current.snapshot)return;
   checking=true;
   try {
    const [current,summary]=await Promise.all([scenariosApi.configuration(projectId,scenarioId),scenariosApi.get(projectId,scenarioId)]);
    if(cancelled||inFlight.current)return;
    setScenario(summary);
    const old=latest.current;
    if(!old.snapshot)return;
    if(scenarioConflict(current,old.snapshot,JSON.stringify(old.form)!==old.baseline)) setConflict(true);
    else if(current.token!==old.snapshot.token) hydrate(current);
   }catch(e){if(!cancelled)setError(getErrorMessage(e,'No se pudieron actualizar los datos del escenario.'));}
   finally{checking=false;}
  }
  window.addEventListener('focus',refresh);document.addEventListener('visibilitychange',refresh);
  return ()=>{cancelled=true;window.removeEventListener('focus',refresh);document.removeEventListener('visibilitychange',refresh);};
 },[projectId,scenarioId,hydrate]);
 useEffect(()=>{
  if(!dirty&&!busy)return;const warn=(e:BeforeUnloadEvent)=>{e.preventDefault();e.returnValue='';};
  window.addEventListener('beforeunload',warn);return ()=>window.removeEventListener('beforeunload',warn);
 },[dirty,busy]);
 const nodes=snapshot?.nodes.map(n=>({id:n.id,name:n.name,type:n.type})) ?? [];
 async function save() {
  if(!form||!canEdit||inFlight.current||conflict)return;inFlight.current=true;setBusy(true);setError('');
  try {const state=await scenariosApi.saveConfiguration(projectId,scenarioId,prepareScenario(form,nodes));hydrate(state);
   setScenario(await scenariosApi.get(projectId,scenarioId));
   setNotice(state.draft?'Borrador guardado. Hay datos pendientes; el escenario todavía no puede simularse.':'Configuración económica guardada. El escenario está listo para simular.');
  }catch(e){setError(getErrorMessage(e,'No se pudo guardar el escenario.'));if(getErrorStatus(e)===409)setConflict(true);}
  finally{inFlight.current=false;setBusy(false);}
 }
 /** A separate scenario of this version, with the name the person typed, holding the simple equivalent of this one. */
 async function createSimpleCopy(name:string) {
  if(!snapshot?.configuration||!canEdit||inFlight.current||dirty||conflict||snapshot.draft||snapshot.legacyDraft)return;
  inFlight.current=true;setBusy(true);
  let copyId:string|undefined;
  try {
   const created=await scenariosApi.create(projectId,versionId,name);copyId=created.id;
   const current=await scenariosApi.configuration(projectId,created.id);
   // Same version, so the nodes are the very same ones.
   const nodeIds=Object.fromEntries(snapshot.nodes.map(n=>[n.id,n.id]));
   const copy=initialScenario(current);copy.editor={mode:'simple',value:createSimpleCopyDraft(snapshot.configuration,nodeIds)};
   await scenariosApi.saveConfiguration(projectId,created.id,prepareScenario(copy,nodes));
   navigatingAway.current=true;
   navigate(scenarioConfigurationPath(projectId,versionId,created.id));
  }catch(e){
   throw new Error(getErrorMessage(e,'No se pudo completar la copia.')+(copyId?' El escenario nuevo quedó creado, vacío, en la lista de escenarios.':''));
  }
  finally{inFlight.current=false;setBusy(false);}
 }
 async function discard() {
  if(!canEdit||inFlight.current||!window.confirm('¿Descartar los cambios locales y el borrador y cargar la configuración publicada actual?'))return;
  inFlight.current=true;setBusy(true);setError('');
  try { const current=await scenariosApi.configuration(projectId,scenarioId);hydrate(await scenariosApi.discardDraft(projectId,scenarioId,current.token));setConflict(false);setScenario(await scenariosApi.get(projectId,scenarioId)); }
  catch(e){setError(getErrorMessage(e,'No se pudieron descartar los cambios.'));}
  finally{inFlight.current=false;setBusy(false);}
 }
 if(!snapshot||!form||!scenario)return error?<Alert tone="error">{error}</Alert>:<p role="status">Cargando configuración…</p>;
 const disabled=!canEdit||conflict;
 const status=scenarioStatusView(scenario.status);
 return <div className="space-y-6">
 <PageHeader spaced={false}
  back={{to:versionScenariosPath(projectId,versionId),label:<>← Versión (base física){versionName?` / ${versionName}`:''} / {scenario.name}</>}}
  title={`Configuración del Escenario: ${scenario.name}`}
  description={`Versión (base física): ${versionName || '—'}. Acá se configura la parte económica y de simulación; la ejecución se inicia desde Simulación de Escenarios.`}
  actions={<div className="flex flex-col items-end gap-1"><span role="status">{busy?'Guardando…':dirty?'Cambios sin guardar':'Sin cambios pendientes'}</span>
   <span className="text-sm text-ink-600">Estado: <strong>{status.label}</strong></span>
   <Link className="text-sm text-brand-800 underline" to={scenarioHistoryPath(projectId,versionId,scenario.id)}>Historial y resultados</Link></div>}/>
 <AccessFailureAlert message={accessFailure} onRetry={()=>void retryAccess()}/>
 {!canEdit&&!accessFailure&&permissions.reasons.edit&&<Alert>{permissions.reasons.edit}</Alert>}
 {scenario.status==='OUTDATED'&&<Alert tone="error"><strong>{OUTDATED_NOTICE}</strong> {OUTDATED_DETAIL}</Alert>}
 <section aria-labelledby="operation-title"><Card padded={false} className="p-4">
  <h2 id="operation-title" className="text-base font-semibold">Operación</h2>
  <p className="mt-1 text-sm text-ink-600">Los valores de operación de los nodos (capacidades, pérdidas, fechas de puesta en marcha, mantenimiento…) pertenecen a la versión, no al escenario: se editan en cada nodo.</p>
  <Link className="mt-2 inline-block text-sm font-semibold text-brand-800 underline" to={versionOperationPath(versionId)}>Editar operación en la versión</Link>
 </Card></section>
 {error&&<Alert tone="error">{error}</Alert>}{notice&&<Alert>{notice}{snapshot.draftOperationsIgnored&&<Button variant="ghost" disabled={!canEdit||busy} onClick={()=>void discard()}>Descartar borrador</Button>}</Alert>}
 {snapshot.errors.length>0&&<Alert tone="error"><ul>{snapshot.errors.map((e,i)=><li key={i}>{e}</li>)}</ul></Alert>}
 {conflict&&<Alert tone="error">El escenario cambió desde otra pantalla. Tus cambios se conservan; para evitar sobrescribir datos, recargá descartando los cambios afectados.<Button variant="ghost" disabled={busy||!canEdit} onClick={()=>void discard()}>Descartar y recargar</Button></Alert>}
 {blocker.state==='blocked'&&<Alert>Hay cambios sin guardar.<Button variant="ghost" onClick={()=>blocker.reset()}>Seguir editando</Button><Button variant="ghost" disabled={busy} onClick={()=>blocker.proceed()}>Salir sin guardar</Button></Alert>}
 {form.editor.mode==='simple' ?
 <EconomicEditor saveOnly draft={form.editor.value} nodes={nodes} disabled={disabled} busy={busy} dirty={dirty||!!snapshot.draft||!!snapshot.legacyDraft} onChange={value=>setForm({...form,editor:{mode:'simple',value}})} save={()=>void save()} discard={()=>void discard()}/> :
 <Card padded={false} className="space-y-4 p-5">
  <h2 className="text-lg font-semibold">Configuración original conservada</h2>
  <p>Este escenario contiene reglas que el formulario simple no representa. Sus datos y resultados se conservan intactos. Podés crear un escenario nuevo, copia de este, para editarlo con el formulario simple.</p>
  <dl className="grid gap-3 sm:grid-cols-2"><div><dt>Año de inversión</dt><dd>{form.editor.value.startYear}</dd></div><div><dt>Años operativos</dt><dd>{form.editor.value.years}</dd></div><div><dt>Moneda</dt><dd>{form.editor.value.currency}</dd></div><div><dt>Entidades fiscales</dt><dd>{form.editor.value.taxEntities.length}</dd></div></dl>
  <p className="text-sm text-ink-500">La copia usa una entidad al 100 %, ingresos gravados y costos deducibles. Debés revisar importes y vigencias: no reproduce participaciones, quebrantos, transferencias internas ni cobros diferidos.</p>
  <Button disabled={disabled||busy||dirty||!!snapshot.draft||!!snapshot.legacyDraft} title={permissions.reasons.edit ?? undefined} onClick={()=>setCopying(true)}>Crear copia simple</Button>
  {(snapshot.draft||snapshot.legacyDraft)&&<><p>Hay un borrador conservado. Descartalo explícitamente para crear una copia desde la configuración publicada.</p><Button variant="ghost" disabled={!canEdit||busy} onClick={()=>void discard()}>Descartar cambios</Button></>}
 </Card>}
 <ScenarioNameDialog open={copying} title="Crear copia simple" submitLabel="Crear copia"
  description="Se crea un escenario nuevo en esta versión con el equivalente simple de esta configuración. El original y sus resultados se conservan."
  scenarios={[]} onClose={()=>setCopying(false)} onSubmit={createSimpleCopy}/>
 </div>;
}
