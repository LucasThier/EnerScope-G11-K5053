import { useCallback, useEffect, useRef, useState } from 'react';
import { projectsApi } from '../api/projects';
import { versionAccessApi } from '../api/versionAccess';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import type { VersionSummary } from '../types/diagram';
import type { ProjectAccess } from '../types/versionAccess';

interface UseProjectAccess {
  access: ProjectAccess | null;
  summaries: VersionSummary[];
  loading: boolean;
  error: string | null;
  forbidden: boolean;
  reload: () => Promise<void>;
}

export function useProjectAccess(projectId: string | undefined): UseProjectAccess {
  const [access, setAccess] = useState<ProjectAccess | null>(null);
  const [summaries, setSummaries] = useState<VersionSummary[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const latestProject = useRef<string | undefined>(projectId);

  const load = useCallback(async (id: string) => {
    const [accessResult, versionsResult] = await Promise.allSettled([
      versionAccessApi.projectAccess(id),
      projectsApi.listVersions(id),
    ]);
    if (latestProject.current !== id) {
      return;
    }
    if (accessResult.status === 'fulfilled') {
      setAccess(accessResult.value.data.data ?? null);
      setError(null);
      setForbidden(false);
    } else {
      setAccess(null);
      if (getErrorStatus(accessResult.reason) === 403) {
        setForbidden(true);
        setError(null);
      } else {
        setForbidden(false);
        setError(getErrorMessage(accessResult.reason, 'No se pudieron cargar los accesos'));
      }
    }
    setSummaries(
      versionsResult.status === 'fulfilled' ? (versionsResult.value.data.data ?? []) : [],
    );
    setLoadedFor(id);
  }, []);

  useEffect(() => {
    latestProject.current = projectId;
    if (projectId) {
      void load(projectId);
    }
  }, [projectId, load]);

  const reload = useCallback(async () => {
    if (projectId) {
      await load(projectId);
    }
  }, [projectId, load]);

  const current = projectId !== undefined && loadedFor === projectId;
  return {
    access: current ? access : null,
    summaries: current ? summaries : [],
    loading: projectId !== undefined && !current,
    error: current ? error : null,
    forbidden: current && forbidden,
    reload,
  };
}
