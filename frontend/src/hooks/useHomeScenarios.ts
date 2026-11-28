import { useCallback, useEffect, useState } from 'react';
import { comparisonsApi } from '../api/comparisons';
import { getErrorMessage } from '../api/errors';
import { projectsApi } from '../api/projects';
import { scenariosApi } from '../api/scenarios';
import type { ComparisonSummary } from '../types/comparison';
import type { VersionSummary } from '../types/diagram';
import type { ScenarioSummary } from '../types/scenario';
import type { ProjectAccess } from '../types/versionAccess';
import { pickHomeVersion } from '../utils/homeScenarios';
import { useGuardedProjectAccess } from './useGuardedProjectAccess';

export type HomeScenariosState =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'ready'; version: VersionSummary | null; scenarios: ScenarioSummary[] };

interface UseHomeScenarios {
  state: HomeScenariosState;
  access: ProjectAccess | null;
  /** The project's most recent saved comparison, when the person may read comparisons; null otherwise. */
  lastComparison: ComparisonSummary | null;
  retry: () => void;
}

/**
 * What Inicio's "Escenarios" block needs, from calls that already exist (nothing new on the backend): the project's
 * access (cached, shared with the other screens), its versions, then the scenarios of the one `pickHomeVersion`
 * chooses. The last saved comparison is a fourth, optional call that fails silently: it only decorates the block.
 */
export function useHomeScenarios(projectId: string | null, readComparisons: boolean): UseHomeScenarios {
  const { access, failure, retry: retryAccess } = useGuardedProjectAccess(projectId ?? undefined);
  const [attempt, setAttempt] = useState(0);
  const [loaded, setLoaded] = useState<{ projectId: string; attempt: number; state: HomeScenariosState } | null>(null);
  const [comparison, setComparison] = useState<{ projectId: string; value: ComparisonSummary | null } | null>(null);

  useEffect(() => {
    if (!projectId || !access || access.projectId !== projectId) return;
    let cancelled = false;
    const done = (state: HomeScenariosState) => {
      if (!cancelled) setLoaded({ projectId, attempt, state });
    };
    (async () => {
      try {
        const response = await projectsApi.listVersions(projectId);
        const version = pickHomeVersion(response.data.data ?? [], access);
        if (!version) return done({ status: 'ready', version: null, scenarios: [] });
        const scenarios = await scenariosApi.list(projectId, version.id);
        done({ status: 'ready', version, scenarios });
      } catch (error) {
        done({ status: 'error', message: getErrorMessage(error, 'No se pudieron cargar los escenarios.') });
      }
    })();
    return () => { cancelled = true; };
  }, [projectId, access, attempt]);

  useEffect(() => {
    if (!projectId || !readComparisons) return;
    let cancelled = false;
    comparisonsApi.list(projectId)
      .then((list) => {
        const latest = [...list].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())[0] ?? null;
        if (!cancelled) setComparison({ projectId, value: latest });
      })
      .catch(() => { if (!cancelled) setComparison({ projectId, value: null }); });
    return () => { cancelled = true; };
  }, [projectId, readComparisons]);

  const retry = useCallback(() => {
    void retryAccess();
    setAttempt((n) => n + 1);
  }, [retryAccess]);

  const current = loaded && loaded.projectId === projectId && loaded.attempt === attempt ? loaded.state : null;
  const state: HomeScenariosState = failure
    ? { status: 'error', message: failure }
    : current ?? { status: 'loading' };
  return {
    state,
    access,
    lastComparison: comparison && comparison.projectId === projectId ? comparison.value : null,
    retry,
  };
}
