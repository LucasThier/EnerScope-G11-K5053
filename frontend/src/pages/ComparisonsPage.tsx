import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { comparisonsApi } from '../api/comparisons';
import { economicsApi } from '../api/economics';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { PreviousComparisons } from '../components/comparison/PreviousComparisons';
import { ScenarioTile } from '../components/comparison/ScenarioTile';
import { SelectionPanel } from '../components/comparison/SelectionPanel';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { Modal, ModalFooter } from '../components/ui/Modal';
import { SearchField } from '../components/ui/SearchField';
import { SelectField } from '../components/ui/SelectField';
import { useActiveProject } from '../hooks/useActiveProject';
import { useGuardedProjectAccess } from '../hooks/useGuardedProjectAccess';
import type { ComparableScenario, ComparisonSummary } from '../types/comparison';
import { MAX_SCENARIOS, PREVIEW_ID, selectionFromQuery, toRequest, type SelectionRef } from '../utils/comparison';

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
 * Step 1 of a comparison: pick the scenarios (from any project the user can see), make
 * sure each one has an up-to-date simulation — simulating it here if needed — and
 * compare. The result opens in its own screen. Earlier comparisons are listed below.
 */
function ComparisonSelection({ projectId }: { projectId: string }) {
  const navigate = useNavigate();
  const [search] = useSearchParams();
  const { projects } = useActiveProject();
  // Saving and deleting runs needs EDIT_PROJECT (from /access, like every project screen); a read-only
  // member (e.g. Ejecutivo) still compares, and sees the result without it being saved.
  const { access } = useGuardedProjectAccess(projectId);
  const canEdit = !!access && (access.platformAdmin || access.projectPermissions.includes('EDIT_PROJECT'));

  const [browsing, setBrowsing] = useState(projectId);
  const [filter, setFilter] = useState('');
  const [catalogs, setCatalogs] = useState<Record<string, Catalog>>({});
  const [selection, setSelection] = useState<SelectionRef[]>(() => selectionFromQuery(search.get('escenarios')));
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

  // The project being browsed, plus every project a selected scenario belongs to.
  const involved = useMemo(() => [...new Set([browsing, ...selection.map(s => s.projectId)])], [browsing, selection]);
  useEffect(() => {
    involved.filter(project => !catalogs[project]).forEach(project => { void loadCatalog(project); });
  }, [involved, catalogs, loadCatalog]);
  useEffect(() => { void reloadHistory(); }, [reloadHistory]);

  const known = useMemo(() => {
    const map = new Map<string, ComparableScenario>();
    Object.values(catalogs).forEach(catalog => { if (catalog.status === 'ready') catalog.scenarios.forEach(s => map.set(s.versionId, s)); });
    return map;
  }, [catalogs]);
  const selected = selection.map(ref => known.get(ref.versionId)).filter((s): s is ComparableScenario => !!s);

  // While anything involved is simulating, refresh those projects until it finishes.
  const busyKey = useMemo(() => {
    const busy = new Set<string>();
    known.forEach(s => { if ((s.status === 'SIMULATING' || starting[s.versionId]) && involved.includes(s.projectId)) busy.add(s.projectId); });
    return [...busy].sort().join(',');
  }, [known, starting, involved]);
  const timer = useRef<number | null>(null);
  useEffect(() => {
    if (!busyKey) return;
    timer.current = window.setInterval(() => {
      if (document.visibilityState === 'visible') busyKey.split(',').forEach(project => { void loadCatalog(project, true); });
    }, POLL_MS);
    return () => { if (timer.current) window.clearInterval(timer.current); };
  }, [busyKey, loadCatalog]);

  function toggle(scenario: ComparableScenario) {
    setCompareError('');
    setSelection(current => current.some(s => s.versionId === scenario.versionId)
      ? current.filter(s => s.versionId !== scenario.versionId)
      : current.length >= MAX_SCENARIOS ? current : [...current, { projectId: scenario.projectId, versionId: scenario.versionId }]);
  }

  async function simulate(scenario: ComparableScenario) {
    setStarting(current => ({ ...current, [scenario.versionId]: true }));
    setSimulationErrors(current => { const next = { ...current }; delete next[scenario.versionId]; return next; });
    try {
      await economicsApi.submitJob(scenario.projectId, scenario.versionId, ROUNDS, '');
      await loadCatalog(scenario.projectId, true);
    } catch (error) {
      setSimulationErrors(current => ({ ...current, [scenario.versionId]: getErrorMessage(error, 'No se pudo iniciar la simulación.') }));
    } finally {
      setStarting(current => { const next = { ...current }; delete next[scenario.versionId]; return next; });
    }
  }

  async function compare() {
    setComparing(true); setCompareError('');
    try {
      const request = toRequest(selection.map(s => s.versionId));
      if (canEdit) {
        const run = await comparisonsApi.save(projectId, request);
        navigate(`/projects/${projectId}/comparisons/${run.id}`);
      } else {
        const result = await comparisonsApi.preview(projectId, request);
        navigate(`/projects/${projectId}/comparisons/${PREVIEW_ID}`, { state: { result, selection } });
      }
    } catch (error) {
      setCompareError(getErrorMessage(error, 'No se pudieron comparar los escenarios.'));
      selection.forEach(s => { void loadCatalog(s.projectId, true); });
    } finally {
      setComparing(false);
    }
  }

  const catalog = catalogs[browsing];
  const visible = catalog?.status === 'ready'
    ? catalog.scenarios.filter(s => s.versionName.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()))
    : [];
  const browsingName = projects.find(p => p.id === browsing)?.name;

  return <div className="relative space-y-10">
    <header className="max-w-3xl">
      <p className="text-sm font-semibold text-brand-800">Paso 1 de 2</p>
      <h1 className="mt-1 text-3xl font-semibold text-ink-800">Comparar escenarios</h1>
      <p className="mt-2 text-base text-ink-600">
        Elegí qué escenarios querés poner lado a lado. Se usa la última simulación de cada uno; si un escenario cambió
        o nunca se simuló, lo podés simular acá mismo.
      </p>
    </header>

    <div className="grid grid-cols-1 gap-6 lg:grid-cols-[minmax(0,1fr)_22rem] lg:items-start">
      <section aria-labelledby="scenario-catalog" className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-end">
          <div className="sm:w-72">
            <SelectField label="Proyecto" value={browsing} onChange={event => { setBrowsing(event.target.value); setFilter(''); }}>
              {!projects.some(p => p.id === projectId) && <option value={projectId}>Proyecto actual</option>}
              {projects.map(p => <option key={p.id} value={p.id}>{p.name}</option>)}
            </SelectField>
          </div>
          <div className="sm:flex-1">
            <SearchField label="Buscar escenario" value={filter} onChange={event => setFilter(event.target.value)} />
          </div>
        </div>
        <h2 id="scenario-catalog" className="sr-only">Escenarios de {browsingName ?? 'este proyecto'}</h2>

        {!catalog || catalog.status === 'loading' ? <p role="status" className="text-sm text-ink-500">Cargando escenarios…</p>
          : catalog.status === 'error' ? <Alert tone="error">{catalog.message}{' '}
              <Button variant="ghost" size="compact" onClick={() => loadCatalog(browsing)}>Reintentar</Button></Alert>
          : !catalog.scenarios.length ? <Card><p className="text-sm text-ink-500">Este proyecto todavía no tiene escenarios.</p></Card>
          : !visible.length ? <p className="text-sm text-ink-500">Ningún escenario coincide con la búsqueda.</p>
          : <ul className="grid grid-cols-1 gap-4 sm:grid-cols-2">
              {visible.map(scenario => {
                const position = selection.findIndex(s => s.versionId === scenario.versionId);
                return <ScenarioTile key={scenario.versionId} scenario={scenario} position={position}
                  disabled={selection.length >= MAX_SCENARIOS} starting={!!starting[scenario.versionId]}
                  error={simulationErrors[scenario.versionId] ?? null}
                  onToggle={() => toggle(scenario)} onSimulate={() => simulate(scenario)} />;
              })}
            </ul>}
      </section>

      <aside className="lg:sticky lg:top-4">
        <SelectionPanel selected={selected} starting={starting} comparing={comparing}
          onRemove={versionId => setSelection(current => current.filter(s => s.versionId !== versionId))}
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
