import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
import { scenariosApi } from '../api/scenarios';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import { isProjectAccessLoss } from '../utils/activeProject';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import { versionEconomics } from '../utils/versionAccessRules';
import { scenarioPermissions, scenarioStatusView } from '../utils/scenarioRules';
import { scenarioHistoryPath, scenarioResultsPath, versionSimulationsPath } from '../utils/scenarioRoutes';
import { dateTime, irrText, money, percent } from '../utils/economicFormat';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { LoadingState } from '../components/ui/LoadingState';
import { tableBodyCell, tableHeaderCell } from '../components/ui/tableClasses';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EconomicJobs } from '../components/economics/EconomicJobs';
import { ScenarioResults } from '../components/economics/ScenarioResults';
import { ProbabilisticResults } from '../components/economics/ProbabilisticResults';
import type { Evaluation } from '../types/economics';
export function EconomicsPage() {
 const {projectId='',versionId='',scenarioId='',tab='history',evaluationId}=useParams();
 if (tab !== 'history' && tab !== 'results') return <Navigate replace to={scenarioHistoryPath(projectId,versionId,scenarioId)} />;
 return <Results key={projectId+scenarioId+String(evaluationId)} projectId={projectId} versionId={versionId} scenarioId={scenarioId} tab={tab} evaluationId={evaluationId}/>;
}
function Results({projectId,versionId,scenarioId,tab,evaluationId}:{projectId:string;versionId:string;scenarioId:string;tab:string;evaluationId?:string}) {
 const {reload:reloadProjects}=useActiveProject();
 const [evaluations,setEvaluations]=useState<Evaluation[]>([]),[detail,setDetail]=useState<Evaluation|null>(null);
 const [name,setName]=useState(''),[error,setError]=useState(''),[loading,setLoading]=useState(true),[refresh,setRefresh]=useState(0);
 const {access,failure:accessFailure,retry:retryAccess}=useGuardedProjectAccess(projectId);
 const {canViewResults}=versionEconomics(access,versionId);
 const canEdit=scenarioPermissions(access,versionId).canSimulate;
 const [status,setStatus]=useState<ReturnType<typeof scenarioStatusView>|null>(null);
 useEffect(()=>{
  let cancelled=false;
  Promise.all([scenariosApi.get(projectId,scenarioId),scenariosApi.evaluations(projectId,scenarioId),evaluationId?scenariosApi.evaluation(projectId,scenarioId,evaluationId):Promise.resolve(null)])
   .then(([summary,history,selected])=>{if(cancelled)return;
    if(summary.versionId!==versionId)throw new Error('Escenario no disponible.');
    setName(summary.name);setStatus(scenarioStatusView(summary.status));setEvaluations(history);setDetail(selected);setError('');
   }).catch(e=>{if(cancelled)return;if(isProjectAccessLoss(getErrorStatus(e)))void reloadProjects();setError(getErrorMessage(e,'No se pudieron cargar las evaluaciones.'));})
   .finally(()=>{if(!cancelled)setLoading(false);});
  return ()=>{cancelled=true;};
 },[projectId,versionId,scenarioId,evaluationId,refresh,reloadProjects]);
 const selected=detail ?? (!evaluationId?evaluations[0]:null);
 return <div className="space-y-6"><PageHeader spaced={false}
  back={{to:versionSimulationsPath(projectId,versionId),label:<>← Simulación de Escenarios / {name}</>}}
  title={`Evaluación del escenario${name?`: ${name}`:''}`}>
  {status&&<p className="mt-1 text-sm text-ink-600">Estado: <strong>{status.label}</strong>{status.label==='Desactualizado'?' — La base cambió: revisá este escenario.':''}</p>}
 </PageHeader>
 <nav className="flex gap-5 border-b border-ink-200 pb-3" aria-label="Evaluaciones"><Link className={tab==='history'?'border-b-2 border-brand-800 font-semibold text-brand-800':'text-ink-600'} to={scenarioHistoryPath(projectId,versionId,scenarioId)} aria-current={tab==='history'?'page':undefined}>Historial</Link><Link className={tab==='results'?'border-b-2 border-brand-800 font-semibold text-brand-800':'text-ink-600'} to={scenarioResultsPath(projectId,versionId,scenarioId)} aria-current={tab==='results'?'page':undefined}>Resultados</Link></nav>
 <AccessFailureAlert message={accessFailure} onRetry={()=>void retryAccess()}/>
 {!canViewResults&&<Alert tone="error">No tenés permiso para ver los resultados económicos de esta versión.</Alert>}
 {tab==='history' && canViewResults && <EconomicJobs projectId={projectId} versionId={versionId} scenarioId={scenarioId} canEdit={canEdit} refreshToken={refresh}/>}
 {error&&<Alert tone="error">{error}</Alert>}
 {!canViewResults?null:loading?<LoadingState>Cargando evaluaciones…</LoadingState>:tab==='history'?<Card>
 <div className="mb-4 flex justify-between gap-3"><h2 className="text-lg font-semibold">Evaluaciones guardadas</h2><Button variant="ghost" onClick={()=>setRefresh(n=>n+1)}>Actualizar</Button></div>
 {!evaluations.length?<EmptyState>Todavía no hay evaluaciones guardadas.</EmptyState>:<div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Fecha y hora','Moneda','WACC','VAN / media','TIR / media calculable','Acción'].map(h=><th key={h} className={tableHeaderCell}>{h}</th>)}</tr></thead><tbody>
 {evaluations.map(e=><tr className="border-t border-ink-100" key={e.id}><td className={tableBodyCell}>{dateTime(e.createdAt)}</td><td className={tableBodyCell}>{e.snapshot.configuration?.currency ?? '—'}</td><td className={tableBodyCell}>{percent(e.snapshot.configuration?.wacc)}</td><td className={tableBodyCell}>{money(e.snapshot.summary?.npv.mean ?? e.snapshot.result?.npv,e.snapshot.configuration?.currency)}</td><td className={tableBodyCell}>{e.snapshot.summary?percent(e.snapshot.summary.irr.mean):irrText(e.snapshot.result?.indicators?.irr)}</td><td className={tableBodyCell}><Link className="text-brand-800 underline" to={scenarioResultsPath(projectId,versionId,scenarioId,e.id)}>Ver evaluación →</Link></td></tr>)}
 </tbody></table></div>}
 </Card>:tab==='results'?<>
 <Alert>Resultados guardados. Los cambios actuales no modifican esta evaluación.</Alert>
 {selected ? selected.snapshot.schemaVersion===3?<ProbabilisticResults key={selected.id} evaluation={selected} projectId={projectId} scenarioId={scenarioId}/>:<ScenarioResults key={selected.id} evaluation={selected}/>:<Card>Todavía no hay resultados. Iniciá una ejecución desde Simulación de Escenarios.</Card>}
 </>:<Alert>Sección no disponible.</Alert>}
 </div>;
}
