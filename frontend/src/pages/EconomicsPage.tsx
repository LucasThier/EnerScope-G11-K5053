import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../hooks/useAuth';
import { dateTime, irrText, money, percent } from '../utils/economicFormat';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { EconomicJobs } from '../components/economics/EconomicJobs';
import { ScenarioResults } from '../components/economics/ScenarioResults';
import { ProbabilisticResults } from '../components/economics/ProbabilisticResults';
import type { Evaluation } from '../types/economics';
export function EconomicsPage() {
 const {projectId='',versionId='',tab='history',evaluationId}=useParams();
 return <Results key={projectId+versionId+String(evaluationId)} projectId={projectId} versionId={versionId} tab={tab} evaluationId={evaluationId}/>;
}
function Results({projectId,versionId,tab,evaluationId}:{projectId:string;versionId:string;tab:string;evaluationId?:string}) {
 const {user}=useAuth();
 const [evaluations,setEvaluations]=useState<Evaluation[]>([]),[detail,setDetail]=useState<Evaluation|null>(null);
 const [name,setName]=useState(''),[error,setError]=useState(''),[loading,setLoading]=useState(true),[refresh,setRefresh]=useState(0),[canEdit,setCanEdit]=useState(false);
 const base='/projects/'+projectId+'/versions/'+versionId+'/economics';
 useEffect(()=>{
  let cancelled=false;
  Promise.all([economicsApi.scenarios(projectId),economicsApi.history(projectId,versionId),evaluationId?economicsApi.evaluation(projectId,versionId,evaluationId):Promise.resolve(null),
    user?.platformRole==='ADMIN'?Promise.resolve(null):projectsApi.members(projectId)])
   .then(([scenarios,history,selected,members])=>{if(cancelled)return;
    setName(scenarios.find(s=>s.id===versionId)?.name ?? 'Escenario');setEvaluations(history);setDetail(selected);setError('');
    setCanEdit(user?.platformRole==='ADMIN'||!!members?.data.data?.some(m=>m.userId===user?.id&&m.active&&m.permissions.includes('EDIT_PROJECT')));
   }).catch(e=>{if(!cancelled)setError(getErrorMessage(e,'No se pudieron cargar las evaluaciones.'));})
   .finally(()=>{if(!cancelled)setLoading(false);});
  return ()=>{cancelled=true;};
 },[projectId,versionId,evaluationId,refresh,user?.id,user?.platformRole]);
 const selected=detail ?? (!evaluationId?evaluations[0]:null);
 return <div className="space-y-5"><Link className="text-brand-800 underline" to={'/projects/'+projectId+'/versions'}>← Simulación de Escenarios / {name}</Link>
 <h1 className="text-2xl font-semibold">Evaluación del escenario</h1>
 <nav className="flex gap-5 border-b border-ink-200 pb-3" aria-label="Evaluaciones"><Link to={base+'/history'} aria-current={tab==='history'?'page':undefined}>Historial</Link><Link to={base+'/results'} aria-current={tab==='results'?'page':undefined}>Resultados</Link></nav>
 <EconomicJobs projectId={projectId} versionId={versionId} canEdit={canEdit} refreshToken={refresh}/>
 {error&&<Alert tone="error">{error}</Alert>}
 {loading?<p role="status">Cargando evaluaciones…</p>:tab==='history'?<Card>
 <div className="mb-4 flex justify-between gap-3"><h2 className="text-lg font-semibold">Evaluaciones guardadas</h2><Button variant="ghost" onClick={()=>setRefresh(n=>n+1)}>Actualizar</Button></div>
 {!evaluations.length?<p>Todavía no hay evaluaciones guardadas.</p>:<div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Fecha y hora','Moneda','WACC','VAN / media','TIR / media calculable','Acción'].map(h=><th key={h} className="p-3">{h}</th>)}</tr></thead><tbody>
 {evaluations.map(e=><tr className="border-t border-ink-100" key={e.id}><td className="p-3">{dateTime(e.createdAt)}</td><td className="p-3">{e.snapshot.configuration?.currency ?? '—'}</td><td className="p-3">{percent(e.snapshot.configuration?.wacc)}</td><td className="p-3">{money(e.snapshot.summary?.npv.mean ?? e.snapshot.result?.npv,e.snapshot.configuration?.currency)}</td><td className="p-3">{e.snapshot.summary?percent(e.snapshot.summary.irr.mean):irrText(e.snapshot.result?.indicators?.irr)}</td><td className="p-3"><Link className="text-brand-800 underline" to={base+'/results/'+e.id}>Ver evaluación →</Link></td></tr>)}
 </tbody></table></div>}
 </Card>:tab==='results'?<>
 <Alert>Resultados guardados. Los cambios actuales no modifican esta evaluación.</Alert>
 {selected ? selected.snapshot.schemaVersion===3?<ProbabilisticResults key={selected.id} evaluation={selected} projectId={projectId} versionId={versionId}/>:<ScenarioResults key={selected.id} evaluation={selected}/>:<Card>Todavía no hay resultados. Iniciá una ejecución desde Simulación de Escenarios.</Card>}
 </>:<Alert>Sección no disponible.</Alert>}
 </div>;
}
