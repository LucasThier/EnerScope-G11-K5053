import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { economicsApi } from '../../api/economics';
import { getErrorMessage } from '../../api/errors';
import { executionError } from '../../utils/probabilisticResults';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
export function SimulationDialog({ projectId, versionId, name, onClose }: { projectId:string;versionId:string;name:string;onClose:()=>void }) {
 const dialog=useRef<HTMLDialogElement>(null), inFlight=useRef(false);
 const [rounds,setRounds]=useState('10'),[seed,setSeed]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const navigate=useNavigate();
 useEffect(()=>{ const el=dialog.current!,previous=document.activeElement as HTMLElement|null;el.showModal();
 return ()=>{el.close();previous?.focus();}; },[]);
 async function submit() {
  if(inFlight.current)return;
  const issue=executionError(rounds,seed);if(issue){setError(issue);return;}
  inFlight.current=true;setBusy(true);setError('');
  try { await economicsApi.submitJob(projectId,versionId,Number(rounds),seed);
   navigate('/projects/'+projectId+'/versions/'+versionId+'/economics/history');
  } catch(e) { setError(getErrorMessage(e,'No se pudo confirmar la simulación. Revisá el historial antes de volver a intentar.')); }
  finally {inFlight.current=false;setBusy(false);}
 }
 return <dialog ref={dialog} aria-labelledby="simulation-title" onCancel={e=>{e.preventDefault();if(!busy)onClose();}}
 className="fixed inset-0 m-auto w-[min(92vw,30rem)] rounded-xl border border-ink-200 bg-white p-6 shadow-xl backdrop:bg-black/40">
 <form className="space-y-4" onSubmit={e=>{e.preventDefault();void submit();}}>
 <h2 id="simulation-title" className="text-xl font-semibold">Simular escenario</h2><p>{name}</p>
 <TextField label="Rondas (1–100)" type="number" min={1} max={100} step={1} value={rounds} disabled={busy} onChange={e=>setRounds(e.target.value)}/>
 <TextField label="Semilla (opcional)" value={seed} disabled={busy} placeholder="Generar automáticamente" onChange={e=>setSeed(e.target.value)}/>
 <p className="text-sm text-ink-500">Se utilizará la configuración guardada del escenario.</p>
 {error && <Alert tone="error">{error}</Alert>}
 <div className="flex justify-end gap-3"><Button variant="ghost" disabled={busy} onClick={onClose}>Cancelar</Button><Button type="submit" loading={busy}>Simular</Button></div>
 </form></dialog>;
}
