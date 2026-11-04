import { useEffect, useState } from 'react';
import { Link, Navigate, useParams } from 'react-router-dom';
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
 if (tab !== 'history' && tab !== 'results') return <Navigate replace to={'/projects/'+projectId+'/versions/'+versionId+'/economics/history'} />;
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
 const [revisionFilter,setRevisionFilter]=useState('all');
 const filteredEvaluations=evaluations.filter(e=>revisionFilter==='all'||(e.revision?.id??'legacy')===revisionFilter);
 const selected=detail ?? (!evaluationId?evaluations[0]:null);
 return <div className="space-y-5"><Link className="text-brand-800 underline" to={'/projects/'+projectId+'/simulations'}>← Simulación de Escenarios / {name}</Link>
 <h1 className="text-2xl font-semibold">Evaluación del escenario</h1>
 <nav className="flex gap-2 border-b border-ink-200" aria-label="Evaluaciones">
  {([{id:'history',label:'Historial'},{id:'results',label:'Resultados'}] as const).map(item=><Link
   key={item.id} to={base+'/'+item.id} aria-current={tab===item.id?'page':undefined}
   className={'-mb-px rounded-t-lg border-b-2 px-4 py-3 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-500 focus-visible:ring-offset-2 '+
    (tab===item.id?'border-brand-700 bg-brand-50 font-semibold text-brand-800':'border-transparent text-ink-500 hover:bg-ink-50 hover:text-ink-800')}
  >{item.label}</Link>)}
 </nav>
 {tab==='history'&&<EconomicJobs projectId={projectId} versionId={versionId} canEdit={canEdit} refreshToken={refresh}/>}
 {error&&<Alert tone="error">{error}</Alert>}
 {loading?<p role="status">Cargando evaluaciones…</p>:tab==='history'?<Card>
 <div className="mb-4 flex justify-between gap-3"><h2 className="text-lg font-semibold">Evaluaciones guardadas</h2><Button variant="ghost" onClick={()=>setRefresh(n=>n+1)}>Actualizar</Button></div>
 <label className="mb-4 flex flex-col gap-1 text-sm">Revisión<select className="rounded border border-ink-300 p-2 focus:ring-2 focus:ring-brand-600" value={revisionFilter} onChange={event=>setRevisionFilter(event.target.value)}><option value="all">Todas las revisiones</option>{Array.from(new Map(evaluations.filter(e=>e.revision).map(e=>[e.revision!.id,e.revision!])).values()).sort((a,b)=>b.number-a.number).map(r=><option key={r.id} value={r.id}>Revisión {r.number}</option>)}<option value="legacy">Históricos sin revisión</option></select></label>
 {!evaluations.length?<p>Todavía no hay evaluaciones guardadas.</p>:<div className="overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr>{['Fecha y hora','Revisión','Moneda','WACC','VAN / media','TIR / media calculable','Acción'].map(h=><th key={h} className="p-3">{h}</th>)}</tr></thead><tbody>
 {filteredEvaluations.map(e=><tr className="border-t border-ink-100" key={e.id}><td className="p-3">{dateTime(e.createdAt)}</td><td className="p-3">{e.revision?`R${e.revision.number}${e.revision.outdated?' · Desactualizado':' · Actual'}`:'Histórico sin revisión'}</td><td className="p-3">{e.snapshot.configuration?.currency ?? '—'}</td><td className="p-3">{percent(e.snapshot.configuration?.wacc)}</td><td className="p-3">{money(e.snapshot.summary?.npv.mean ?? e.snapshot.result?.npv,e.snapshot.configuration?.currency)}</td><td className="p-3">{e.snapshot.summary?percent(e.snapshot.summary.irr.mean):irrText(e.snapshot.result?.indicators?.irr)}</td><td className="p-3"><Link className="text-brand-800 underline" to={base+'/results/'+e.id}>Ver evaluación →</Link></td></tr>)}
 </tbody></table></div>}
 </Card>:tab==='results'?<>
 {selected&&<p className="text-sm">{name} · {selected.revision?`Revisión ${selected.revision.number} · ${selected.revision.outdated?'Desactualizada':'Actual'}`:'Histórico sin revisión reproducible'} · Simulación {selected.id}</p>}
 <Alert>Resultados guardados. Los cambios actuales no modifican esta evaluación.</Alert>
 {selected ? selected.snapshot.summary?<ProbabilisticResults key={selected.id} evaluation={selected} projectId={projectId} versionId={versionId}/>:<ScenarioResults key={selected.id} evaluation={selected}/>:<Card>Todavía no hay resultados. Iniciá una ejecución desde Simulación de Escenarios.</Card>}
 </>:<Alert>Sección no disponible.</Alert>}
 </div>;
}
