import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { scenariosApi } from '../../api/scenarios';
import { getErrorMessage } from '../../api/errors';
import { executionError } from '../../utils/probabilisticResults';
import { scenarioHistoryPath } from '../../utils/scenarioRoutes';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { TextField } from '../ui/TextField';
export function SimulationDialog({ projectId, versionId, scenarioId, name, onClose }: { projectId:string;versionId:string;scenarioId:string;name:string;onClose:()=>void }) {
 const inFlight=useRef(false);
 const [rounds,setRounds]=useState('10'),[seed,setSeed]=useState(''),[error,setError]=useState(''),[busy,setBusy]=useState(false);
 const navigate=useNavigate();
 async function submit() {
  if(inFlight.current)return;
  const issue=executionError(rounds,seed);if(issue){setError(issue);return;}
  inFlight.current=true;setBusy(true);setError('');
  try { await scenariosApi.simulate(projectId,scenarioId,Number(rounds),seed);
   navigate(scenarioHistoryPath(projectId,versionId,scenarioId));
  } catch(e) { setError(getErrorMessage(e,'No se pudo confirmar la simulación. Revisá el historial antes de volver a intentar.')); }
  finally {inFlight.current=false;setBusy(false);}
 }
 // While the request is in flight nothing closes the dialog: not Escape, not the scrim, not the close button.
 return <Modal open onClose={()=>{if(!busy)onClose();}} title="Simular escenario" subtitle={name}>
 <form className="space-y-4" onSubmit={e=>{e.preventDefault();void submit();}}>
 <TextField label="Rondas (1–100)" type="number" min={1} max={100} step={1} value={rounds} disabled={busy} onChange={e=>setRounds(e.target.value)}/>
 <TextField label="Semilla (opcional)" value={seed} disabled={busy} placeholder="Generar automáticamente" onChange={e=>setSeed(e.target.value)}/>
 <p className="text-sm text-ink-500">Se utilizará la configuración guardada del escenario y los nodos actuales de su versión.</p>
 {error && <Alert tone="error">{error}</Alert>}
 <ModalFooter><Button variant="ghost" disabled={busy} onClick={onClose}>Cancelar</Button><Button type="submit" loading={busy}>Simular</Button></ModalFooter>
 </form></Modal>;
}
