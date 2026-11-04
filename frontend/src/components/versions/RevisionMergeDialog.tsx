import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { scenarioMergesApi } from '../../api/scenarioMerges';
import type { PreparedMerge } from '../../api/scenarioMerges';
import { getErrorMessage } from '../../api/errors';
import { Modal } from '../ui/Modal';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
export function RevisionMergeDialog({ projectId, source, target, onClose }: {
 projectId: string; source: { id: string; name: string }; target: { id: string; name: string }; onClose: () => void;
}) {
 const [merge, setMerge] = useState<PreparedMerge | null>(null);
 const [choices, setChoices] = useState<Record<string, 'SOURCE' | 'TARGET'>>({});
 const [busy, setBusy] = useState(true);
 const [error, setError] = useState('');
 useEffect(() => {
  let cancelled = false;
  scenarioMergesApi.get(projectId, target.id).then(value => { if (!cancelled) { setMerge(value); setBusy(false); } })
   .catch(reason => { if (!cancelled) { setError(getErrorMessage(reason, 'No se pudo consultar la integración.')); setBusy(false); } });
  return () => { cancelled = true; };
 }, [projectId, target.id]);
 async function apply() {
  if (busy) return; setBusy(true); setError('');
  try { setMerge(merge ? await scenarioMergesApi.resolve(projectId, target.id, merge.token, choices) : await scenarioMergesApi.prepare(projectId, source.id)); }
  catch (reason) { setError(getErrorMessage(reason, 'No se pudo preparar la integración.')); }
  finally { setBusy(false); }
 }
 return <Modal open title="Integrar operación" subtitle={`${source.name} → ${target.name}`} onClose={() => { if (!busy) onClose(); }} size="lg">
  <div className="space-y-4">
   <p>Se utilizarán las revisiones publicadas. La configuración económica del destino se conserva. Un administrador deberá revisar y publicar el borrador desde Configuración.</p>
   {error && <Alert tone="error">{error}</Alert>}
   {merge && !merge.baseRevisionId && <Alert tone="info">No se conoce la revisión de origen de esta rama antigua. Revisá explícitamente cada diferencia.</Alert>}
   {merge?.conflicts.map(conflict => {
    const key = `${conflict.kind}:${conflict.identity}`;
    return <fieldset key={key} className="rounded-lg border border-ink-200 p-3">
     <legend className="px-1 text-sm font-semibold">{conflict.kind === 'nodes' ? 'Nodo' : 'Conexión'} · {conflict.identity}</legend>
     <details><summary className="cursor-pointer text-sm underline">Ver diferencias</summary><div className="grid gap-2 sm:grid-cols-2">
      <div><strong>Origen</strong><pre className="max-h-48 overflow-auto whitespace-pre-wrap text-xs">{conflict.source === null ? 'Eliminado' : JSON.stringify(conflict.source, null, 2)}</pre></div>
      <div><strong>Destino</strong><pre className="max-h-48 overflow-auto whitespace-pre-wrap text-xs">{conflict.target === null ? 'Eliminado' : JSON.stringify(conflict.target, null, 2)}</pre></div>
     </div></details>
     <label className="mt-3 flex flex-col gap-1 text-sm">Conservar
      <select disabled={busy} value={choices[key] ?? ''} onChange={event => setChoices({ ...choices, [key]: event.target.value as 'SOURCE' | 'TARGET' })} className="rounded border border-ink-300 p-2 focus:ring-2 focus:ring-brand-600">
       <option value="" disabled>Seleccioná una resolución</option><option value="SOURCE">Origen</option><option value="TARGET">Destino</option>
      </select>
     </label>
    </fieldset>;
   })}
   {merge?.conflicts.length === 0 ? <div className="space-y-3"><p>La operación está preparada en el borrador. Todavía no se publicó una revisión.</p><Link className="text-brand-800 underline" to={`/projects/${projectId}/versions/${target.id}/economics/configuration`}>Revisar configuración y publicar →</Link></div>
    : <Button disabled={busy || (!!merge && merge.conflicts.some(conflict => !choices[`${conflict.kind}:${conflict.identity}`]))} onClick={() => void apply()}>{busy ? 'Procesando…' : merge ? 'Guardar resoluciones' : 'Preparar integración'}</Button>}
   <Button variant="ghost" disabled={busy} onClick={onClose}>Cerrar</Button>
  </div>
 </Modal>;
}
