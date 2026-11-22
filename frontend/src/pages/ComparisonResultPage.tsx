import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router-dom';
import { comparisonsApi } from '../api/comparisons';
import { getErrorMessage } from '../api/errors';
import { ComparisonResults } from '../components/comparison/ComparisonResults';
import { Alert } from '../components/ui/Alert';
import type { ComparisonResult, SavedComparison } from '../types/comparison';
import { PREVIEW_ID, scenarioNames, selectionToQuery } from '../utils/comparison';

type State = { status: 'loading' } | { status: 'error'; message: string } | { status: 'ready'; comparison: SavedComparison };

export function ComparisonResultPage() {
  const { projectId = '', comparisonId = '' } = useParams();
  const location = useLocation();
  if (comparisonId === PREVIEW_ID) {
    const result = (location.state as { result?: ComparisonResult } | null)?.result ?? null;
    return <UnsavedResultView projectId={projectId} result={result} />;
  }
  return <ComparisonResultView key={projectId + comparisonId} projectId={projectId} comparisonId={comparisonId} />;
}

/** A result compared by a read-only member: shown from navigation state, never saved. */
function UnsavedResultView({ projectId, result }: { projectId: string; result: ComparisonResult | null }) {
  const back = `/projects/${projectId}/comparisons`;
  if (!result) return <div className="space-y-4">
    <Alert tone="info">Esta comparación no se guardó y ya no está disponible. Volvé a elegir los escenarios.</Alert>
    <Link to={back} className="text-sm font-semibold text-brand-800 hover:underline">← Volver a elegir escenarios</Link>
  </div>;
  const again = back + '?escenarios=' + encodeURIComponent(selectionToQuery(result.scenarios
    .filter(s => s.scenarioId && !s.deleted).map(s => ({ projectId, scenarioId: s.scenarioId as string }))));
  const names = scenarioNames(result);
  return <div className="relative space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <Link to={back} className="text-sm font-semibold text-brand-800 hover:underline">← Comparar otros escenarios</Link>
        <p className="mt-3 text-sm font-semibold text-brand-800">Paso 2 de 2 · Resultado</p>
        <h1 className="mt-1 text-3xl font-semibold text-ink-800">{result.scenarios.map(s => names[s.evaluationId]).join(' vs ')}</h1>
        <p className="mt-1 text-sm text-ink-500">Tu rol en el proyecto es de solo lectura: esta comparación no se guarda.</p>
      </div>
      <Link to={again} className="inline-flex h-control items-center rounded-control border border-control-border bg-surface px-4 text-sm font-semibold text-ink-800 hover:bg-ink-50">
        Modificar la selección
      </Link>
    </header>
    <ComparisonResults result={result} />
  </div>;
}

/** Step 2: the result of one comparison run, with the simulations it was made with. */
function ComparisonResultView({ projectId, comparisonId }: { projectId: string; comparisonId: string }) {
  const [state, setState] = useState<State>({ status: 'loading' });

  useEffect(() => {
    let cancelled = false;
    comparisonsApi.get(projectId, comparisonId)
      .then(comparison => { if (!cancelled) setState({ status: 'ready', comparison }); })
      .catch(error => { if (!cancelled) setState({ status: 'error', message: getErrorMessage(error, 'No se pudo abrir la comparación.') }); });
    return () => { cancelled = true; };
  }, [projectId, comparisonId]);

  const back = `/projects/${projectId}/comparisons`;
  if (state.status === 'loading') return <p role="status" className="text-sm text-ink-500">Cargando la comparación…</p>;
  if (state.status === 'error') return <div className="space-y-4">
    <Alert tone="error">{state.message}</Alert>
    <Link to={back} className="text-sm font-semibold text-brand-800 hover:underline">← Volver a elegir escenarios</Link>
  </div>;

  const { comparison } = state;
  // Deleted scenarios cannot be chosen again, so they are left out of "Modificar la selección".
  const again = back + '?escenarios=' + encodeURIComponent(selectionToQuery(comparison.result.scenarios
    .filter(s => s.scenarioId && !s.deleted).map(s => ({ projectId, scenarioId: s.scenarioId as string }))));

  return <div className="relative space-y-6">
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="min-w-0">
        <Link to={back} className="text-sm font-semibold text-brand-800 hover:underline">← Comparar otros escenarios</Link>
        <p className="mt-3 text-sm font-semibold text-brand-800">Paso 2 de 2 · Resultado</p>
        <h1 className="mt-1 text-3xl font-semibold text-ink-800">{comparison.name}</h1>
        <p className="mt-1 text-sm text-ink-500">
          Comparación hecha el {new Date(comparison.createdAt).toLocaleString('es-AR', { dateStyle: 'long', timeStyle: 'short' })},
          con la última simulación que tenía cada escenario en ese momento.
        </p>
      </div>
      <Link to={again} className="inline-flex h-control items-center rounded-control border border-control-border bg-surface px-4 text-sm font-semibold text-ink-800 hover:bg-ink-50">
        Modificar la selección
      </Link>
    </header>
    <ComparisonResults result={comparison.result} />
  </div>;
}
