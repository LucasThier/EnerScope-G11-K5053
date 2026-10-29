import { scenarioConfigurationApi } from '../api/scenarioConfiguration';
import type { ScenarioReadiness } from '../types/scenarioConfiguration';
import { SimulationDialog } from '../components/economics/SimulationDialog';
import { projectsApi } from '../api/projects';
import { useAuth } from '../hooks/useAuth';
import { useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { getErrorMessage } from '../api/errors';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { formatDate } from '../utils/date';
import type { Scenario } from '../types/economics';

export function ScenariosPage({ configuration = false }: { configuration?: boolean }) {
  const { projectId = '' } = useParams();
  return <ScenariosWorkspace key={projectId + String(configuration)} projectId={projectId} configuration={configuration} />;
}
function ScenariosWorkspace({ projectId, configuration }: { projectId: string; configuration: boolean }) {
  const [readiness,setReadiness]=useState<ScenarioReadiness[]>([]);
  const [simulating,setSimulating]=useState<Scenario|null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();
  const [canEdit, setCanEdit] = useState(false);
  const [creating, setCreating] = useState(false);
  const [name, setName] = useState('');
  const [parent, setParent] = useState('');
  const [busy, setBusy] = useState(false);
  const [createError, setCreateError] = useState('');
  const inFlight = useRef(false);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  const [state, setState] = useState<{ project: string; scenarios: Scenario[]; error: string; loading: boolean }>({ project: '', scenarios: [], error: '', loading: true });
  const [search, setSearch] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false;
    Promise.all([configuration ? Promise.resolve([]) : scenarioConfigurationApi.readiness(projectId), economicsApi.scenarios(projectId), user?.platformRole === 'ADMIN' ? Promise.resolve(null) : projectsApi.members(projectId)]).then(([ready, scenarios, members]) => {
      if (!cancelled) setReadiness(ready);
      if (!cancelled) setCanEdit(user?.platformRole === 'ADMIN' || !!members?.data.data?.some(m => m.userId === user?.id && m.active && m.permissions.includes('EDIT_PROJECT')));
      if (!cancelled) setState({ project: projectId, scenarios, error: '', loading: false });
    }).catch(error => { if (!cancelled) setState({ project: projectId, scenarios: [], error: getErrorMessage(error, 'No se pudieron cargar los escenarios.'), loading: false }); });
    return () => { cancelled = true; };
  }, [projectId, retry, user?.id, user?.platformRole, configuration]);
  async function create() {
    if (inFlight.current || !canEdit || !name.trim() || (parent && !state.scenarios.some(s => s.id === parent))) return;
    inFlight.current = true; setBusy(true); setCreateError('');
    try {
      const response = await projectsApi.createVersion(projectId, { name: name.trim(), parentVersion: parent || null });
      if (!response.data.data?.id) throw new Error('No se recibió el escenario creado.');
      if (mounted.current) navigate('/projects/' + projectId + '/versions/' + response.data.data.id + '/economics/configuration');
    } catch (e) { if (mounted.current) setCreateError(getErrorMessage(e, 'No se pudo crear el escenario.')); }
    finally { inFlight.current = false; if (mounted.current) setBusy(false); }
  }
  const loading = state.loading || state.project !== projectId;
  const filtered = state.scenarios.filter(s => s.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  return <div className="space-y-6">
    <header><h1 className="text-2xl font-semibold text-ink-800">{configuration ? 'Configuración del Escenario' : 'Simulación de Escenarios'}</h1>
      <p className="mt-1 text-sm text-ink-500">{configuration ? 'Seleccioná un escenario para editar su configuración económica y operativa.' : 'Seleccioná Simular para elegir las rondas y la semilla. El nombre del escenario abre su historial y resultados.'}</p></header>
    {configuration && canEdit && <Button onClick={() => setCreating(true)} disabled={busy}>Nuevo escenario</Button>}
    {configuration && creating && canEdit && <Card><form className="space-y-4" onSubmit={e => { e.preventDefault(); void create(); }}>
      <h2 className="text-lg font-semibold">Crear escenario</h2>
      <p className="text-sm text-ink-500">El escenario se crea como una versión de este proyecto. Podés comenzar vacío o copiar la estructura física de una versión existente.</p>
      <TextField label="Nombre del escenario" required maxLength={120} value={name} disabled={busy} onChange={e => setName(e.target.value)} />
      <label className="flex flex-col gap-2 text-sm">Versión de origen<select className="rounded-lg border border-ink-200 bg-white p-3" value={parent} disabled={busy} onChange={e => setParent(e.target.value)}><option value="">Escenario vacío</option>{state.scenarios.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
      {createError && <Alert tone="error">{createError}</Alert>}
      <div className="flex gap-3"><Button type="submit" loading={busy} disabled={!name.trim()}>Crear y configurar</Button><Button variant="ghost" disabled={busy} onClick={() => setCreating(false)}>Cancelar</Button></div>
    </form></Card>}
    {loading ? <p role="status">Cargando escenarios…</p> : state.error ? <Alert tone="error">{state.error} <Button variant="ghost" onClick={() => { setState(s => ({ ...s, loading: true })); setRetry(n => n + 1); }}>Reintentar</Button></Alert> :
      <Card><TextField label="Buscar escenario" value={search} onChange={e => setSearch(e.target.value)} />
        {!state.scenarios.length ? <p className="mt-6 text-sm text-ink-500">Este proyecto todavía no tiene escenarios. Creá un escenario para configurar su economía y agregá sus nodos desde el mapa de la cadena de valor.</p> :
          <div className="mt-6 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b border-ink-200"><th className="p-3">Escenario</th><th className="p-3">Actualizado</th><th className="p-3">Acción</th></tr></thead><tbody>
            {filtered.map(s => {
              const status=readiness.find(r=>r.versionId===s.id);
              return <tr key={s.id} className="border-b border-ink-100"><td className="p-3 font-medium">
                <Link className="text-brand-800 underline" to={`/projects/${projectId}/versions/${s.id}/economics/${configuration ? 'configuration' : 'history'}`}>{s.name}</Link>
                {!configuration && <p className="mt-1 text-xs text-ink-500">{status?.ready ? 'Listo para simular' : status?.errors.join(' ') || 'No se pudo verificar la configuración.'}</p>}
              </td><td className="p-3">{formatDate(s.lastModified)}</td><td className="p-3">
                {configuration ? <Link className="text-brand-800 underline" to={`/projects/${projectId}/versions/${s.id}/economics/configuration`}>Configurar →</Link> :
                  <Button disabled={!canEdit || !status?.ready} onClick={()=>setSimulating(s)}>Simular</Button>}
              </td></tr>;
            })}
          </tbody></table>{!filtered.length && <p className="p-3">No hay escenarios que coincidan con la búsqueda.</p>}</div>}
      </Card>}
    {simulating && <SimulationDialog projectId={projectId} versionId={simulating.id} name={simulating.name} onClose={()=>setSimulating(null)} />}
  </div>;
}
