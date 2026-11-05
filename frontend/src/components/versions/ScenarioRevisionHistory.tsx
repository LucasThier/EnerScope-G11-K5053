import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import { scenarioRevisionsApi } from '../../api/scenarioRevisions';
import type { ScenarioRevisionSummary, ScenarioRevisionDetail } from '../../api/scenarioRevisions';
import { getErrorMessage } from '../../api/errors';
import { Alert } from '../ui/Alert';
import { Modal } from '../ui/Modal';

export function ScenarioRevisionHistory({ projectId, scenarios }: {
 projectId: string; scenarios: { id: string; name: string }[];
}) {
 const navigate = useNavigate();
 const [canRestore, setCanRestore] = useState(false);
 const [restoring, setRestoring] = useState(false);
 const [selected, setSelected] = useState('');
 const [revisions, setRevisions] = useState<ScenarioRevisionSummary[]>([]);
 const [detail, setDetail] = useState<ScenarioRevisionDetail | null>(null);
 const [inspecting, setInspecting] = useState(false);
 const [error, setError] = useState('');
 const [loading, setLoading] = useState(false);
 const scenarioId = scenarios.some(item => item.id === selected) ? selected : '';
 useEffect(() => {
  if (!scenarioId) return;
  let cancelled = false;
  Promise.all([scenarioRevisionsApi.list(projectId, scenarioId), scenarioRevisionsApi.status(projectId, scenarioId)]).then(([values,status]) => {
   if (!cancelled) { setRevisions(values); setCanRestore(status.canPublish); setLoading(false); }
  }).catch(reason => {
   if (!cancelled) { setError(getErrorMessage(reason, 'No se pudieron cargar las revisiones.')); setLoading(false); }
  });
  return () => { cancelled = true; };
 }, [projectId, scenarioId]);
 async function inspect(revisionId: string) {
  if (inspecting) return;
  setInspecting(true); setError('');
  try { setDetail(await scenarioRevisionsApi.get(projectId, scenarioId, revisionId)); }
  catch (reason) { setError(getErrorMessage(reason, 'No se pudo consultar la revisión.')); }
  finally { setInspecting(false); }
 }
 async function restore(revisionId: string) {
  if (restoring || !canRestore || !window.confirm('Restaurar esta revisión en el borrador. Se reemplazarán los cambios pendientes del escenario; la revisión publicada y sus resultados se conservarán.')) return;
  setRestoring(true); setError('');
  try {
   const status = await scenarioRevisionsApi.status(projectId, scenarioId);
   await scenarioRevisionsApi.restore(projectId, scenarioId, revisionId, status.token);
   navigate(`/projects/${projectId}/versions/${scenarioId}/economics/configuration`);
  } catch (reason) { setError(getErrorMessage(reason, 'No se pudo restaurar la revisión.')); }
  finally { setRestoring(false); }
 }
 return <section aria-label="Revisiones publicadas" className="mt-6 rounded-xl border border-ink-200 bg-white p-5">
  <h2 className="text-lg font-semibold">Revisiones publicadas</h2>
  <p className="mt-1 text-sm text-ink-600">Cada revisión conserva la configuración económica y operativa de ese momento.</p>
  <label className="mt-4 flex flex-col gap-2 text-sm">Escenario
   <select disabled={restoring || inspecting} value={scenarioId} onChange={event => { setSelected(event.target.value); setRevisions([]); setError(''); setLoading(!!event.target.value); }} className="rounded-lg border border-ink-300 p-2 focus:outline-none focus:ring-2 focus:ring-brand-600">
    <option value="">Seleccioná un escenario</option>
    {scenarios.map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
   </select>
  </label>
  {scenarioId && loading && <p role="status" className="mt-4">Cargando revisiones…</p>}
  {scenarioId && error && <Alert tone="error">{error}</Alert>}
  {scenarioId && !loading && !error && <ul className="mt-4 divide-y divide-ink-200">
   {revisions.map(revision => <li key={revision.id} className="flex flex-wrap justify-between gap-2 py-3">
    <span className="font-medium">Revisión {revision.number}</span>
    <span>{revision.createdAt ? new Date(revision.createdAt).toLocaleString('es-AR') : 'Fecha no disponible'}</span>
    <span className="text-sm text-ink-600">{revision.mergeSourceRevisionId ? 'Integración de rama' : revision.parentRevisionId ? 'Publicación' : 'Estado inicial'}</span>
    <Button variant="ghost" disabled={inspecting} onClick={() => void inspect(revision.id)}>Ver contenido</Button>
    {canRestore && <Button variant="ghost" disabled={restoring} onClick={() => void restore(revision.id)}>Restaurar en borrador</Button>}
   </li>)}
   {revisions.length === 0 && <li>No hay revisiones disponibles.</li>}
  </ul>}
  <Modal open={detail !== null} onClose={() => setDetail(null)} title={detail ? `Revisión ${detail.revision.number}` : 'Revisión'} subtitle="Contenido publicado de solo lectura" size="lg">
   {detail && <div className="space-y-4">
    <dl className="grid grid-cols-2 gap-3 text-sm">
     <div><dt>Moneda</dt><dd>{detail.content.configuration?.currency ?? 'Sin configurar'}</dd></div>
     <div><dt>Años operativos</dt><dd>{detail.content.configuration?.years ?? 'Sin configurar'}</dd></div>
     <div><dt>Nodos</dt><dd>{detail.content.nodes.length}</dd></div>
     <div><dt>Conexiones</dt><dd>{detail.content.connections.length}</dd></div>
    </dl>
    <ul className="divide-y divide-ink-100">{detail.content.nodes.map((node,index) => <li key={String(node.data.identityId ?? index)} className="py-2"><span className="font-medium">{String(node.data.name ?? 'Nodo sin nombre')}</span> · {node.type}</li>)}</ul>
    <details className="rounded border border-ink-200 p-3"><summary className="cursor-pointer focus-visible:outline-2 focus-visible:outline-brand-600">Configuración económica completa</summary><pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-all text-xs">{JSON.stringify(detail.content.configuration,null,2)}</pre></details>
    <details className="rounded border border-ink-200 p-3"><summary className="cursor-pointer focus-visible:outline-2 focus-visible:outline-brand-600">Operación, distribuciones y presentación del mapa</summary><pre className="mt-3 max-h-80 overflow-auto whitespace-pre-wrap break-all text-xs">{JSON.stringify({nodes:detail.content.nodes,connections:detail.content.connections},null,2)}</pre></details>
    <Button variant="secondary" onClick={() => setDetail(null)}>Cerrar contenido</Button>
   </div>}
  </Modal>
 </section>;
}
