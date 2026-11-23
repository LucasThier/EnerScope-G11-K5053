import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { economicsApi } from '../api/economics';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { scenariosApi } from '../api/scenarios';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import { SimulationDialog } from '../components/economics/SimulationDialog';
import { DeleteScenarioDialog, ScenarioNameDialog } from '../components/scenarios/ScenarioDialogs';
import { Alert } from '../components/ui/Alert';
import { EmptyState } from '../components/ui/EmptyState';
import { LoadingState } from '../components/ui/LoadingState';
import { tableBodyCell, tableHeaderCell } from '../components/ui/tableClasses';
import { PageHeader } from '../components/ui/PageHeader';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { useActiveProject } from '../hooks/useActiveProject';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import type { ScenarioSummary } from '../types/scenario';
import { isProjectAccessLoss } from '../utils/activeProject';
import { formatDate } from '../utils/date';
import {
  createBlockedReason, scenarioPermissions, scenarioStatusView, simulateBlockedReason, type StatusTone,
} from '../utils/scenarioRules';
import {
  projectSimulationsPath, projectVersionsPath, scenarioConfigurationPath, scenarioHistoryPath, scenarioResultsPath, versionOperationPath,
} from '../utils/scenarioRoutes';

const POLL_MS = 2500;

const TONES: Record<StatusTone, string> = {
  good: 'bg-brand-50 text-brand-800 ring-brand-200',
  warn: 'bg-warn-50 text-warn-800 ring-warn-200',
  info: 'bg-sky-50 text-sky-800 ring-sky-200',
  neutral: 'bg-ink-50 text-ink-700 ring-ink-200',
};

type Dialog =
  | { kind: 'create' }
  | { kind: 'duplicate'; scenario: ScenarioSummary }
  | { kind: 'rename'; scenario: ScenarioSummary };

/**
 * The scenarios of one version (the physical base): their status, and what each person may do with them.
 * `mode` decides the module: "configuration" edits and manages scenarios; "simulation" simulates them and opens results.
 */
export function VersionScenariosPage({ mode }: { mode: 'configuration' | 'simulation' }) {
  const { projectId = '', versionId = '' } = useParams();
  return <Workspace key={projectId + versionId + mode} projectId={projectId} versionId={versionId} mode={mode} />;
}

function Workspace({ projectId, versionId, mode }: { projectId: string; versionId: string; mode: 'configuration' | 'simulation' }) {
  const navigate = useNavigate();
  const { reload: reloadProjects } = useActiveProject();
  const { access, failure: accessFailure, retry: retryAccess } = useGuardedProjectAccess(projectId);
  const permissions = useMemo(() => scenarioPermissions(access, versionId), [access, versionId]);
  const [scenarios, setScenarios] = useState<ScenarioSummary[] | null>(null);
  const [versionName, setVersionName] = useState('');
  const [error, setError] = useState('');
  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [deleting, setDeleting] = useState<ScenarioSummary | null>(null);
  const [simulating, setSimulating] = useState<ScenarioSummary | null>(null);
  const [retry, setRetry] = useState(0);

  const load = useCallback(async (quiet: boolean) => {
    try {
      const next = await scenariosApi.list(projectId, versionId);
      setScenarios(next);
      if (!quiet) setError('');
    } catch (failure) {
      if (isProjectAccessLoss(getErrorStatus(failure))) void reloadProjects();
      if (!quiet) {
        setScenarios(null);
        setError(getErrorMessage(failure, 'No se pudieron cargar los escenarios.'));
      }
    }
  }, [projectId, versionId, reloadProjects]);

  useEffect(() => { void load(false); }, [load, retry]);
  useEffect(() => {
    let cancelled = false;
    economicsApi.scenarios(projectId)
      .then(versions => { if (!cancelled) setVersionName(versions.find(version => version.id === versionId)?.name ?? ''); })
      .catch(() => { /* the title falls back to a generic one */ });
    return () => { cancelled = true; };
  }, [projectId, versionId]);

  // While a simulation is queued or running, keep the list fresh.
  const busy = scenarios?.some(scenario => scenario.status === 'SIMULATING') ?? false;
  useEffect(() => {
    if (!busy) return;
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(true); }, POLL_MS);
    return () => window.clearInterval(timer);
  }, [busy, load]);

  const count = scenarios?.length ?? 0;
  const createBlock = createBlockedReason(permissions, count);
  const backPath = mode === 'configuration' ? projectVersionsPath(projectId) : projectSimulationsPath(projectId);
  const title = mode === 'configuration' ? 'Escenarios de la versión' : 'Simulación de escenarios';

  return <div className="space-y-6">
    <PageHeader
      spaced={false}
      back={{
        to: backPath,
        label: <>← Versión (base física){versionName ? ` / ${versionName}` : ''}</>,
      }}
      title={`${title}${versionName ? `: ${versionName}` : ''}`}
      description={
        <span className="block max-w-3xl">
          {mode === 'configuration'
            ? 'Un escenario es la configuración económica y de simulación que se apoya en esta versión. Los valores de operación de los nodos pertenecen a la versión.'
            : 'Elegí un escenario para simularlo o ver sus resultados. Cada escenario tiene su propio historial.'}
        </span>
      }
      actions={
        <>
          <Link className="text-sm font-semibold text-brand-800 underline" to={versionOperationPath(versionId)}>Editar operación en la versión</Link>
          {mode === 'configuration' && (
            <Button disabled={createBlock !== null} title={createBlock ?? undefined} onClick={() => setDialog({ kind: 'create' })}>
              Nuevo escenario
            </Button>
          )}
        </>
      }
    >
      {mode === 'configuration' && createBlock && <p role="note" className="mt-2 text-sm text-ink-500">{createBlock}</p>}
    </PageHeader>

    <AccessFailureAlert message={accessFailure} onRetry={() => void retryAccess()} />
    {error && <Alert tone="error">{error}{' '}<Button variant="ghost" size="compact" onClick={() => setRetry(n => n + 1)}>Reintentar</Button></Alert>}

    {!error && scenarios === null ? <LoadingState>Cargando escenarios…</LoadingState> : scenarios && (
      <Card>
        {!scenarios.length ? <EmptyState>Esta versión todavía no tiene escenarios.</EmptyState> : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead><tr className="border-b border-ink-100">
                <th className={tableHeaderCell}>Escenario</th><th className={tableHeaderCell}>Estado</th><th className={tableHeaderCell}>Última simulación</th><th className={tableHeaderCell}>Actualizado</th><th className={tableHeaderCell}>Acciones</th>
              </tr></thead>
              <tbody>{scenarios.map(scenario => {
                const status = scenarioStatusView(scenario.status);
                const simulateBlock = simulateBlockedReason(permissions, scenario);
                return <tr key={scenario.id} className="border-b border-ink-100 align-top">
                  <td className={`${tableBodyCell} font-medium`}>
                    <Link className="text-brand-800 underline" to={mode === 'configuration'
                      ? scenarioConfigurationPath(projectId, versionId, scenario.id) : scenarioHistoryPath(projectId, versionId, scenario.id)}>{scenario.name}</Link>
                    {scenario.defaultScenario && <span className="ml-2 rounded-full bg-ink-50 px-2 py-0.5 text-xs font-medium text-ink-600">Por defecto</span>}
                    {scenario.draftPending && <p className="mt-1 text-xs text-warn-800">Tiene un borrador pendiente.</p>}
                    {!scenario.configured && <p className="mt-1 text-xs text-ink-500">Sin configuración económica todavía.</p>}
                  </td>
                  <td className={tableBodyCell}>
                    <span className={`inline-block rounded-full px-2.5 py-0.5 text-xs font-semibold ring-1 ring-inset ${TONES[status.tone]}`}>{status.label}</span>
                    {scenario.status === 'OUTDATED' && <p className="mt-1 max-w-xs text-xs text-ink-600">La base cambió: revisá este escenario.</p>}
                  </td>
                  <td className={tableBodyCell}>{scenario.latestEvaluatedAt ? formatDate(scenario.latestEvaluatedAt) : '—'}</td>
                  <td className={tableBodyCell}>{formatDate(scenario.lastModified)}</td>
                  <td className={tableBodyCell}><div className="flex flex-wrap gap-2">
                    {mode === 'configuration' ? <>
                      <Link className="text-brand-800 underline" to={scenarioConfigurationPath(projectId, versionId, scenario.id)}>
                        {permissions.canEdit ? 'Configurar' : 'Ver configuración'}
                      </Link>
                      <Button variant="ghost" size="compact" disabled={createBlock !== null} title={createBlock ?? undefined}
                        onClick={() => setDialog({ kind: 'duplicate', scenario })}>Duplicar</Button>
                      <Button variant="ghost" size="compact" disabled={!permissions.canEdit} title={permissions.reasons.edit ?? undefined}
                        onClick={() => setDialog({ kind: 'rename', scenario })}>Renombrar</Button>
                      <Button variant="ghost" size="compact" disabled={!permissions.canDelete} title={permissions.reasons.delete ?? undefined}
                        onClick={() => setDeleting(scenario)}>Eliminar</Button>
                    </> : <>
                      <Button size="compact" disabled={simulateBlock !== null} title={simulateBlock ?? undefined} onClick={() => setSimulating(scenario)}>Simular</Button>
                      <Link className="self-center text-brand-800 underline" to={scenarioHistoryPath(projectId, versionId, scenario.id)}>Historial</Link>
                      <Link className="self-center text-brand-800 underline" to={scenarioResultsPath(projectId, versionId, scenario.id)}>Resultados</Link>
                    </>}
                  </div>
                  {mode === 'simulation' && simulateBlock && <p className="mt-1 max-w-xs text-xs text-ink-500">{simulateBlock}</p>}
                  </td>
                </tr>;
              })}</tbody>
            </table>
          </div>
        )}
      </Card>
    )}

    <ScenarioNameDialog open={dialog?.kind === 'create'} title="Nuevo escenario" submitLabel="Crear escenario"
      description="Se crea vacío, sobre esta versión. Después lo configurás."
      scenarios={scenarios ?? []} onClose={() => setDialog(null)}
      onSubmit={async name => {
        const created = await scenariosApi.create(projectId, versionId, name);
        navigate(scenarioConfigurationPath(projectId, versionId, created.id));
      }} />
    <ScenarioNameDialog open={dialog?.kind === 'duplicate'} title="Duplicar escenario" submitLabel="Duplicar"
      description={dialog?.kind === 'duplicate' ? `Se copia solo la configuración económica de «${dialog.scenario.name}». Los resultados y borradores no se copian.` : undefined}
      scenarios={scenarios ?? []} onClose={() => setDialog(null)}
      onSubmit={async name => {
        if (dialog?.kind !== 'duplicate') return;
        await scenariosApi.duplicate(projectId, dialog.scenario.id, name);
        await load(false);
      }} />
    <ScenarioNameDialog open={dialog?.kind === 'rename'} title="Renombrar escenario" submitLabel="Guardar nombre"
      initialName={dialog?.kind === 'rename' ? dialog.scenario.name : ''} selfId={dialog?.kind === 'rename' ? dialog.scenario.id : undefined}
      scenarios={scenarios ?? []} onClose={() => setDialog(null)}
      onSubmit={async name => {
        if (dialog?.kind !== 'rename') return;
        await scenariosApi.rename(projectId, dialog.scenario.id, name);
        await load(false);
      }} />
    <DeleteScenarioDialog scenario={deleting} onClose={() => setDeleting(null)}
      onConfirm={async scenario => {
        try {
          await scenariosApi.remove(projectId, scenario.id);
        } catch (failure) {
          // Already gone (deleted from another screen): the list simply refreshes.
          if (getErrorStatus(failure) !== 404) throw failure;
        }
        await load(false);
      }} />
    {simulating && <SimulationDialog projectId={projectId} versionId={versionId} scenarioId={simulating.id} name={simulating.name} onClose={() => setSimulating(null)} />}
  </div>;
}
