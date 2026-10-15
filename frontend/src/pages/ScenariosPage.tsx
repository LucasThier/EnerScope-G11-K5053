import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import type { VersionSummary } from '../types/diagram';
import { formatDate } from '../utils/date';
import { formatDateTime } from '../utils/number';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { RowButton } from '../components/ui/RowButton';
import { Spinner } from '../components/ui/Spinner';
import {
  AlertIcon,
  BranchIcon,
  CompareIcon,
  FlaskIcon,
  MergeIcon,
  PencilIcon,
  PlusIcon,
  TrashIcon,
  ValueChainIcon,
} from '../components/ui/icons';
import { ConflictsModal } from '../components/scenarios/ConflictsModal';
import { CostEstimateCard } from '../components/scenarios/CostEstimateCard';
import { DeleteScenarioDialog } from '../components/scenarios/DeleteScenarioDialog';
import { MergeScenarioDialog } from '../components/scenarios/MergeScenarioDialog';
import { NewScenarioModal } from '../components/scenarios/NewScenarioModal';
import { RenameScenarioModal } from '../components/scenarios/RenameScenarioModal';
import { SimulateModal } from '../components/scenarios/SimulateModal';
import { SimulationRunsPanel } from '../components/scenarios/SimulationRunsPanel';
import { descendantsOf, orderAsTree } from '../components/scenarios/scenarioTree';

/** The backend compares up to six versions at once (VersionComparisonService.MAX_VERSIONS). */
const MAX_COMPARED = 6;

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 text-sm text-ink-700 align-middle';

/**
 * Scenarios of a project — its versions — in one place: the branching tree,
 * the actions that manage it (create, branch, rename, merge into the parent,
 * resolve conflicts, delete), the simulations of the selected scenario with its
 * live cost estimate, and the selection that opens a comparison.
 */
export function ScenariosPage() {
  const { projectId = '' } = useParams();
  const navigate = useNavigate();
  const { projects } = useActiveProject();
  const project = projects.find((p) => p.id === projectId);

  const [versions, setVersions] = useState<VersionSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [compared, setCompared] = useState<string[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const [creatingFrom, setCreatingFrom] = useState<string | null | undefined>(undefined);
  const [renaming, setRenaming] = useState<VersionSummary | null>(null);
  const [deleting, setDeleting] = useState<VersionSummary | null>(null);
  const [merging, setMerging] = useState<VersionSummary | null>(null);
  const [resolving, setResolving] = useState<VersionSummary | null>(null);
  const [simulating, setSimulating] = useState<VersionSummary | null>(null);

  const load = useCallback(async () => {
    if (!projectId) return;
    setError(null);
    try {
      const res = await projectsApi.listVersions(projectId);
      setVersions(res.data.data ?? []);
    } catch (err) {
      if (getErrorStatus(err) === 403) {
        setForbidden(true);
      } else {
        setError(getErrorMessage(err, 'No se pudieron cargar los escenarios'));
      }
      setVersions([]);
    }
  }, [projectId]);

  useEffect(() => {
    setVersions(null);
    setSelectedId(null);
    setCompared([]);
    void load();
  }, [load]);

  const rows = useMemo(() => orderAsTree(versions ?? []), [versions]);
  const byId = useMemo(() => new Map((versions ?? []).map((v) => [v.id, v])), [versions]);
  const selected = (selectedId && byId.get(selectedId)) || rows[0]?.version || null;

  const changed = useCallback(() => {
    setRefreshKey((key) => key + 1);
    void load();
  }, [load]);

  function toggleCompared(id: string) {
    setCompared((list) =>
      list.includes(id) ? list.filter((x) => x !== id) : list.length >= MAX_COMPARED ? list : [...list, id],
    );
  }

  function openComparison() {
    navigate(`/projects/${projectId}/compare?versions=${compared.join(',')}`);
  }

  if (forbidden) {
    return <Alert tone="error">No tenés acceso a este proyecto.</Alert>;
  }

  return (
    <div className="flex flex-col gap-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">Escenarios</h1>
          <p className="mt-1 text-sm text-ink-500">
            {project ? `${project.name} · ` : ''}Cada escenario es una versión del modelo de la cadena de
            valor. Ramificá uno para probar un cambio, simulalo y comparalo con el original.
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button
            variant="ghost"
            onClick={openComparison}
            disabled={compared.length < 2}
            title={compared.length < 2 ? 'Marcá al menos dos escenarios para compararlos' : undefined}
          >
            <CompareIcon className="h-4 w-4" />
            Comparar{compared.length > 0 ? ` (${compared.length})` : ''}
          </Button>
          <Button onClick={() => setCreatingFrom(selected?.id ?? null)}>
            <PlusIcon className="h-4 w-4" />
            Nuevo escenario
          </Button>
        </div>
      </header>

      {error && <Alert tone="error">{error}</Alert>}

      <Card padded={false} className="overflow-hidden">
        {versions === null ? (
          <div className="flex justify-center py-10 text-brand-800">
            <Spinner className="h-6 w-6" />
          </div>
        ) : rows.length === 0 ? (
          <div className="flex flex-col items-center gap-3 px-6 py-10 text-center">
            <p className="text-sm text-ink-500">
              Este proyecto todavía no tiene escenarios. Creá el escenario base y modelalo en el editor.
            </p>
            <Button onClick={() => setCreatingFrom(null)}>Crear escenario base</Button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[60rem] border-collapse">
              <thead>
                <tr className="border-b border-ink-100">
                  <th scope="col" className={`${headerCell} w-10`}>
                    <span className="sr-only">Comparar</span>
                  </th>
                  <th scope="col" className={headerCell}>Escenario</th>
                  <th scope="col" className={`${headerCell} text-right`}>Nodos</th>
                  <th scope="col" className={`${headerCell} text-right`}>Conexiones</th>
                  <th scope="col" className={`${headerCell} text-right`}>Simulaciones</th>
                  <th scope="col" className={headerCell}>Última simulación</th>
                  <th scope="col" className={`${headerCell} text-right`}>Acciones</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {rows.map(({ version, depth }) => {
                  const isSelected = selected?.id === version.id;
                  const comparedIndex = compared.indexOf(version.id);
                  const parent = version.parentVersionId ? byId.get(version.parentVersionId) : undefined;
                  return (
                    <tr
                      key={version.id}
                      className={isSelected ? 'bg-brand-50/60' : 'hover:bg-ink-50'}
                      onClick={() => setSelectedId(version.id)}
                    >
                      <td className={bodyCell} onClick={(e) => e.stopPropagation()}>
                        <input
                          type="checkbox"
                          checked={comparedIndex >= 0}
                          disabled={comparedIndex < 0 && compared.length >= MAX_COMPARED}
                          onChange={() => toggleCompared(version.id)}
                          aria-label={`Comparar ${version.name}`}
                          className="h-4 w-4 accent-brand-800"
                        />
                      </td>
                      <td className={bodyCell}>
                        <div className="flex items-center gap-2" style={{ paddingLeft: depth * 20 }}>
                          {depth > 0 && <span className="text-ink-400" aria-hidden="true">↳</span>}
                          <button
                            type="button"
                            onClick={() => setSelectedId(version.id)}
                            className="text-left font-semibold text-ink-800 hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                          >
                            {version.name}
                          </button>
                          {comparedIndex === 0 && compared.length > 1 && (
                            <span className="rounded-full bg-ink-100 px-2 py-0.5 text-xs font-medium text-ink-600">
                              base
                            </span>
                          )}
                        </div>
                        <div className="text-xs text-ink-500" style={{ paddingLeft: depth * 20 + (depth > 0 ? 18 : 0) }}>
                          {parent ? `Parte de ${parent.name} · ` : ''}Creado {formatDate(version.createdAt)}
                        </div>
                      </td>
                      <td className={`${bodyCell} text-right tabular-nums`}>{version.nodeCount}</td>
                      <td className={`${bodyCell} text-right tabular-nums`}>{version.connectionCount}</td>
                      <td className={`${bodyCell} text-right tabular-nums`}>{version.simulationCount}</td>
                      <td className={`${bodyCell} whitespace-nowrap`}>
                        {version.lastSimulatedAt ? formatDateTime(version.lastSimulatedAt) : 'Nunca'}
                      </td>
                      <td className={`${bodyCell} text-right`} onClick={(e) => e.stopPropagation()}>
                        <div className="flex justify-end gap-1">
                          <Link
                            to={`/projects/${projectId}/editor?version=${version.id}`}
                            aria-label={`Abrir ${version.name} en el editor`}
                            title="Abrir en el editor"
                            className="inline-flex rounded-lg p-1 text-ink-500 hover:bg-ink-50 hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400"
                          >
                            <ValueChainIcon className="h-4 w-4" />
                          </Link>
                          <RowButton
                            label={`Simular ${version.name}`}
                            title="Simular"
                            onClick={() => setSimulating(version)}
                          >
                            <FlaskIcon className="h-4 w-4" />
                          </RowButton>
                          <RowButton
                            label={`Ramificar ${version.name}`}
                            title="Nuevo escenario a partir de este"
                            onClick={() => setCreatingFrom(version.id)}
                          >
                            <BranchIcon className="h-4 w-4" />
                          </RowButton>
                          <RowButton label={`Renombrar ${version.name}`} title="Renombrar" onClick={() => setRenaming(version)}>
                            <PencilIcon className="h-4 w-4" />
                          </RowButton>
                          {parent && (
                            <RowButton
                              label={`Fusionar ${version.name} en ${parent.name}`}
                              title="Fusionar en el escenario padre"
                              onClick={() => setMerging(version)}
                            >
                              <MergeIcon className="h-4 w-4" />
                            </RowButton>
                          )}
                          <RowButton
                            label={`Ver los conflictos de ${version.name}`}
                            title="Conflictos"
                            onClick={() => setResolving(version)}
                          >
                            <AlertIcon className="h-4 w-4" />
                          </RowButton>
                          <RowButton label={`Eliminar ${version.name}`} title="Eliminar" onClick={() => setDeleting(version)}>
                            <TrashIcon className="h-4 w-4" />
                          </RowButton>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {selected && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          <div className="lg:col-span-2">
            <SimulationRunsPanel
              projectId={projectId}
              version={selected}
              refreshKey={refreshKey}
              onSimulate={() => setSimulating(selected)}
              onChanged={() => void load()}
            />
          </div>
          <CostEstimateCard version={selected} refreshKey={refreshKey} />
        </div>
      )}

      <NewScenarioModal
        open={creatingFrom !== undefined}
        onClose={() => setCreatingFrom(undefined)}
        projectId={projectId}
        versions={versions ?? []}
        defaultParentId={creatingFrom ?? null}
        onCreated={(created) => {
          setSelectedId(created.id);
          changed();
        }}
      />
      <RenameScenarioModal version={renaming} onClose={() => setRenaming(null)} onRenamed={changed} />
      <DeleteScenarioDialog
        version={deleting}
        descendants={deleting ? descendantsOf(deleting.id, versions ?? []) : []}
        onClose={() => setDeleting(null)}
        onDeleted={() => {
          setCompared([]);
          setSelectedId(null);
          changed();
        }}
      />
      <MergeScenarioDialog
        version={merging}
        parent={merging?.parentVersionId ? byId.get(merging.parentVersionId) ?? null : null}
        onClose={() => setMerging(null)}
        onMerged={changed}
      />
      <ConflictsModal version={resolving} onClose={() => setResolving(null)} onResolved={changed} />
      {simulating && (
        <SimulateModal
          open
          onClose={() => setSimulating(null)}
          versionId={simulating.id}
          versionName={simulating.name}
          onDone={(detail) => {
            const target = simulating;
            setSimulating(null);
            changed();
            navigate(`/projects/${projectId}/scenarios/${target.id}/runs/${detail.summary.id}`);
          }}
        />
      )}
    </div>
  );
}
