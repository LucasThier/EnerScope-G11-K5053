import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { comparisonsApi } from '../api/comparisons';
import { scenariosApi } from '../api/scenarios';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { PreviousComparisons } from '../components/comparison/PreviousComparisons';
import { ScenarioTile } from '../components/comparison/ScenarioTile';
import { SelectionPanel } from '../components/comparison/SelectionPanel';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Modal, ModalFooter } from '../components/ui/Modal';
import { SearchField } from '../components/ui/SearchField';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import type { ComparableScenario, ComparisonSummary } from '../types/comparison';
import {
  MAX_SCENARIOS, PREVIEW_ID, filterScenarios, groupByVersion, resolveSelection, selectionFromQuery, selectionNotice, toRequest, type SelectionRef,
} from '../utils/comparison';

/** Rounds used when a scenario is simulated from the comparison screen. */
const ROUNDS = 10;
const POLL_MS = 2500;

type Catalog = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; scenarios: ComparableScenario[] };

export function ComparisonsPage() {
  const { projectId = '' } = useParams();
  // Remount per project so nothing of one project leaks into another.
  return <ComparisonSelection key={projectId} projectId={projectId} />;
}

/**
 * Step 1 of a comparison: pick the scenarios of this project, make
 * sure each one has an up-to-date simulation — simulating it here if needed — and
 * compare. The result opens in its own screen. Earlier comparisons are listed below.
 */
function ComparisonSelection({ projectId }: { projectId: string }) {
  const navigate = useNavigate();
  const [search] = useSearchParams();
  // Saving and deleting runs needs EDIT_PROJECT (from /access, like every project screen); a read-only
  // member (e.g. Ejecutivo) still compares, and sees the result without it being saved.
  const { access } = useGuardedProjectAccess(projectId);
  const canEdit = !!access && (access.platformAdmin || access.projectPermissions.includes('EDIT_PROJECT'));

  const [filter, setFilter] = useState('');
  const [catalogs, setCatalogs] = useState<Record<string, Catalog>>({});
  const [selection, setSelection] = useState<SelectionRef[]>(() => selectionFromQuery(search.get('escenarios'), projectId));
  const [selectionNote, setSelectionNote] = useState<string | null>(null);
  const [selectionChecked, setSelectionChecked] = useState(false);
  const [starting, setStarting] = useState<Record<string, boolean>>({});
  const [simulationErrors, setSimulationErrors] = useState<Record<string, string>>({});
  const [comparing, setComparing] = useState(false);
  const [compareError, setCompareError] = useState('');
  const [history, setHistory] = useState<ComparisonSummary[] | null>(null);
  const [deleting, setDeleting] = useState<ComparisonSummary | null>(null);

  const loadCatalog = useCallback(async (project: string, quiet = false) => {
    if (!quiet) setCatalogs(current => current[project]?.status === 'ready' ? current : { ...current, [project]: { status: 'loading' } });
    try {
      const scenarios = await comparisonsApi.scenarios(project);
      setCatalogs(current => ({ ...current, [project]: { status: 'ready', scenarios } }));
    } catch (error) {
      if (!quiet) setCatalogs(current => ({ ...current, [project]: { status: 'error', message: getErrorMessage(error, 'No se pudieron cargar los escenarios.') } }));
    }
  }, []);

  const reloadHistory = useCallback(() => comparisonsApi.list(projectId).then(setHistory).catch(() => setHistory([])), [projectId]);

  // A comparison only holds scenarios of the project in the URL.
  useEffect(() => {
    if (!catalogs[projectId]) void loadCatalog(projectId);
  }, [projectId, catalogs, loadCatalog]);
  useEffect(() => { void reloadHistory(); }, [reloadHistory]);

  const known = useMemo(() => {
    const map = new Map<string, ComparableScenario>();
    Object.values(catalogs).forEach(catalog => { if (catalog.status === 'ready') catalog.scenarios.forEach(s => map.set(s.scenarioId, s)); });
    return map;
  }, [catalogs]);
  const selected = selection.map(ref => known.get(ref.scenarioId)).filter((s): s is ComparableScenario => !!s);

  // The selection that arrives in the URL is checked once against the project's scenarios: an old link that names a version
  // means that version's default scenario, and an id that no longer exists is dropped, saying so.
  const readyCatalog = catalogs[projectId]?.status === 'ready' ? catalogs[projectId] : null;
  useEffect(() => {
    if (selectionChecked || !readyCatalog || readyCatalog.status !== 'ready') return;
    const resolved = resolveSelection(selection, readyCatalog.scenarios);
    setSelection(resolved.refs);
    setSelectionNote(selectionNotice(resolved));
    setSelectionChecked(true);
  }, [selectionChecked, readyCatalog, selection]);

  // While anything is simulating, refresh the project's scenarios until it finishes.
  const busy = useMemo(() => [...known.values()].some(s => s.status === 'SIMULATING' || starting[s.scenarioId]), [known, starting]);
  const timer = useRef<number | null>(null);
  useEffect(() => {
    if (!busy) return;
    timer.current = window.setInterval(() => {
      if (document.visibilityState === 'visible') void loadCatalog(projectId, true);
    }, POLL_MS);
    return () => { if (timer.current) window.clearInterval(timer.current); };
  }, [busy, projectId, loadCatalog]);

  function toggle(scenario: ComparableScenario) {
    setCompareError('');
    setSelection(current => current.some(s => s.scenarioId === scenario.scenarioId)
      ? current.filter(s => s.scenarioId !== scenario.scenarioId)
      : current.length >= MAX_SCENARIOS ? current : [...current, { projectId: scenario.projectId, scenarioId: scenario.scenarioId }]);
  }

  async function simulate(scenario: ComparableScenario) {
    setStarting(current => ({ ...current, [scenario.scenarioId]: true }));
    setSimulationErrors(current => { const next = { ...current }; delete next[scenario.scenarioId]; return next; });
    try {
      await scenariosApi.simulate(scenario.projectId, scenario.scenarioId, ROUNDS, '');
      await loadCatalog(scenario.projectId, true);
    } catch (error) {
      setSimulationErrors(current => ({ ...current, [scenario.scenarioId]: getErrorMessage(error, 'No se pudo iniciar la simulación.') }));
    } finally {
      setStarting(current => { const next = { ...current }; delete next[scenario.scenarioId]; return next; });
    }
  }

  async function compare() {
    setComparing(true); setCompareError('');
    try {
      const request = toRequest(selection.map(s => s.scenarioId));
      if (canEdit) {
        const run = await comparisonsApi.save(projectId, request);
        navigate(`/projects/${projectId}/comparisons/${run.id}`);
      } else {
        const result = await comparisonsApi.preview(projectId, request);
        navigate(`/projects/${projectId}/comparisons/${PREVIEW_ID}`, { state: { result, selection } });
      }
    } catch (error) {
      setCompareError(getErrorMessage(error, 'No se pudieron comparar los escenarios.'));
      void loadCatalog(projectId, true);
    } finally {
      setComparing(false);
    }
  }

  const catalog = catalogs[projectId];
  const visible = catalog?.status === 'ready' ? filterScenarios(catalog.scenarios, filter) : [];
  const groups = groupByVersion(visible);

  return <div className="relative space-y-10">
    <header className="max-w-3xl">
      <p className="text-sm font-semibold text-brand-800">Paso 1 de 2</p>
      <h1 className="mt-1 text-3xl font-semibold text-ink-800">Comparar escenarios</h1>
      <p className="mt-2 text-base text-ink-600">
        Elegí qué escenarios querés poner lado a lado, de la misma versión o de versiones distintas del proyecto. Se usa la última simulación de cada uno; si un escenario cambió
        o nunca se simuló, lo podés simular acá mismo.
      </p>
    </header>

    {selectionNote && <Alert tone="info">{selectionNote}</Alert>}

    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
      <section aria-labelledby="scenario-catalog" className="flex min-w-0 flex-col gap-4">
        <SearchField label="Buscar escenario" value={filter} onChange={event => setFilter(event.target.value)} />
        <h2 id="scenario-catalog" className="sr-only">Escenarios de este proyecto</h2>

        {!catalog || catalog.status === 'loading' ? <p role="status" className="text-sm text-ink-500">Cargando escenarios…</p>
          : catalog.status === 'error' ? <Alert tone="error">{catalog.message}{' '}
              <Button variant="ghost" size="compact" onClick={() => loadCatalog(projectId)}>Reintentar</Button></Alert>
          : !catalog.scenarios.length ? <Card><p className="text-sm text-ink-500">Este proyecto todavía no tiene escenarios.</p></Card>
          : !visible.length ? <p className="text-sm text-ink-500">Ningún escenario coincide con la búsqueda.</p>
          : groups.map(group => <div key={group.versionId} className="flex flex-col gap-3">
              <h3 className="text-sm font-semibold text-ink-700">Versión (base física): {group.versionName}</h3>
              <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                {group.scenarios.map(scenario => {
                  const position = selection.findIndex(s => s.scenarioId === scenario.scenarioId);
                  return <ScenarioTile key={scenario.scenarioId} scenario={scenario} position={position}
                    disabled={selection.length >= MAX_SCENARIOS} starting={!!starting[scenario.scenarioId]}
                    error={simulationErrors[scenario.scenarioId] ?? null}
                    onToggle={() => toggle(scenario)} onSimulate={() => simulate(scenario)} />;
                })}
              </ul>
            </div>)}
      </section>

      <aside className="lg:sticky lg:top-4">
        <SelectionPanel selected={selected} starting={starting} comparing={comparing}
          onRemove={scenarioId => setSelection(current => current.filter(s => s.scenarioId !== scenarioId))}
          onSimulatePending={pending => pending.forEach(scenario => { void simulate(scenario); })}
          onCompare={compare} />
        {compareError && <Alert className="mt-3" tone="error">{compareError}</Alert>}
      </aside>
    </div>

    {history && <PreviousComparisons projectId={projectId} comparisons={history} canDelete={canEdit} onDelete={setDeleting} />}

    <DeleteComparisonDialog comparison={deleting} onClose={() => setDeleting(null)} onDelete={async id => {
      try {
        await comparisonsApi.remove(projectId, id);
      } catch (error) {
        if (getErrorStatus(error) !== 404) throw error;
      }
      await reloadHistory();
      setDeleting(null);
    }} />
  </div>;
}

function DeleteComparisonDialog({ comparison, onClose, onDelete }: {
  comparison: ComparisonSummary | null; onClose: () => void; onDelete: (id: string) => Promise<void>;
}) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  useEffect(() => { if (comparison) setError(''); }, [comparison]);

  async function confirm() {
    if (!comparison) return;
    setBusy(true); setError('');
    try {
      await onDelete(comparison.id);
    } catch (failure) {
      setError(getErrorMessage(failure, 'No se pudo eliminar la comparación.'));
    } finally {
      setBusy(false);
    }
  }

  return <Modal open={comparison !== null} onClose={onClose} title="Eliminar comparación">
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error}</Alert>}
      <p className="text-sm text-ink-700">Se va a eliminar la comparación <span className="font-semibold text-ink-800">{comparison?.name}</span>.
        Los escenarios y sus simulaciones no se modifican.</p>
      <ModalFooter>
        <Button variant="ghost" onClick={onClose}>Cancelar</Button>
        <Button variant="danger" onClick={confirm} loading={busy}>Eliminar</Button>
      </ModalFooter>
    </div>
  </Modal>;
}
