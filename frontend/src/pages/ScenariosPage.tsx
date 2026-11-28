import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { LoadingState } from '../components/ui/LoadingState';
import { tableBodyCell, tableHeaderCell } from '../components/ui/tableClasses';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import { useActiveProject } from '../hooks/useActiveProject';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import type { Scenario } from '../types/economics';
import { isProjectAccessLoss } from '../utils/activeProject';
import { formatDate } from '../utils/date';
import { scenarioPermissions } from '../utils/scenarioRules';
import { versionScenariosPath, versionSimulationsPath } from '../utils/scenarioRoutes';

/**
 * Step 1 of the scenario flow: choose the version (the physical base). Its scenarios — the economic and simulation
 * configuration — are listed in the next screen, where they are created, configured and simulated.
 */
export function ScenariosPage({ configuration = false }: { configuration?: boolean }) {
  const { projectId = '' } = useParams();
  return <VersionsWorkspace key={projectId + String(configuration)} projectId={projectId} configuration={configuration} />;
}

function VersionsWorkspace({ projectId, configuration }: { projectId: string; configuration: boolean }) {
  const { reload: reloadProjects } = useActiveProject();
  const { access, failure: accessFailure, retry: retryAccess } = useGuardedProjectAccess(projectId);
  const [state, setState] = useState<{ project: string; versions: Scenario[]; error: string; loading: boolean }>({ project: '', versions: [], error: '', loading: true });
  const [search, setSearch] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    let cancelled = false;
    economicsApi.scenarios(projectId).then(versions => {
      if (!cancelled) setState({ project: projectId, versions, error: '', loading: false });
    }).catch(error => {
      if (cancelled) return;
      if (isProjectAccessLoss(getErrorStatus(error))) void reloadProjects();
      setState({ project: projectId, versions: [], error: getErrorMessage(error, 'No se pudieron cargar las versiones.'), loading: false });
    });
    return () => { cancelled = true; };
  }, [projectId, retry, reloadProjects]);
  const loading = state.loading || state.project !== projectId;
  const filtered = state.versions.filter(version => version.name.toLocaleLowerCase().includes(search.toLocaleLowerCase()));
  const opensTo = (versionId: string) => (configuration ? versionScenariosPath(projectId, versionId) : versionSimulationsPath(projectId, versionId));
  return <div className="space-y-6">
    <PageHeader
      spaced={false}
      title={configuration ? 'Configuración del Escenario' : 'Simulación de Escenarios'}
      description={<>Cada versión es la base física de la red. Elegí una para {configuration ? 'ver y configurar sus escenarios: la parte económica y de simulación.' : 'simular sus escenarios y ver sus resultados.'}</>}
    />
    <AccessFailureAlert message={accessFailure} onRetry={() => void retryAccess()} />
    {loading ? <LoadingState>Cargando versiones…</LoadingState> : state.error ? <Alert tone="error">{state.error} <Button variant="ghost" onClick={() => { setState(s => ({ ...s, loading: true })); setRetry(n => n + 1); }}>Reintentar</Button></Alert> :
      <Card><TextField label="Buscar versión" value={search} onChange={e => setSearch(e.target.value)} />
        {!state.versions.length ? <EmptyState>Este proyecto todavía no tiene versiones disponibles.</EmptyState> :
          <div className="mt-6 overflow-x-auto"><table className="w-full text-left text-sm"><thead><tr className="border-b border-ink-100"><th className={tableHeaderCell}>Versión (base física)</th><th className={tableHeaderCell}>Actualizado</th><th className={tableHeaderCell}>Acción</th></tr></thead><tbody>
            {filtered.map(version => {
              const rights = scenarioPermissions(access, version.id);
              return <tr key={version.id} className="border-b border-ink-100"><td className={`${tableBodyCell} font-medium`}>
                <Link className="text-brand-800 underline" to={opensTo(version.id)}>{version.name}</Link>
              </td><td className={tableBodyCell}>{formatDate(version.lastModified)}</td><td className={tableBodyCell}>
                {rights.canView ? <Link className="text-brand-800 underline" to={opensTo(version.id)}>Ver escenarios</Link> : <span className="text-ink-500">Sin acceso a esta versión</span>}
              </td></tr>;
            })}
          </tbody></table>{!filtered.length && <EmptyState>No hay versiones que coincidan con la búsqueda.</EmptyState>}</div>}
      </Card>}
  </div>;
}
