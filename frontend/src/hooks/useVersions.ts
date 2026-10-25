import { useCallback, useEffect, useRef, useState } from 'react';
import { projectsApi } from '../api/projects';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import type { VersionSummary } from '../types/diagram';

interface CreateVersionData {
  name: string;
  parentVersion?: string | null;
}

interface UseVersions {
  versions: VersionSummary[];
  loading: boolean;
  error: string | null;
  errorStatus: number | null;
  reload: () => Promise<void>;
  createVersion: (data: CreateVersionData) => Promise<string>;
  deleteVersion: (versionId: string) => Promise<void>;
  mergeVersion: (subId: string) => Promise<void>;
}

export function useVersions(projectId: string | null): UseVersions {
  const [versions, setVersions] = useState<VersionSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [errorStatus, setErrorStatus] = useState<number | null>(null);
  const loadedProjectRef = useRef<string | null>(null);

  const reload = useCallback(async () => {
    if (!projectId) {
      loadedProjectRef.current = null;
      setVersions([]);
      setLoading(false);
      return;
    }
    const isInitialLoad = loadedProjectRef.current !== projectId;
    if (isInitialLoad) {
      setLoading(true);
    }
    setError(null);
    setErrorStatus(null);
    try {
      const res = await projectsApi.listVersions(projectId);
      setVersions(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar las versiones'));
      setErrorStatus(getErrorStatus(err));
    } finally {
      loadedProjectRef.current = projectId;
      if (isInitialLoad) {
        setLoading(false);
      }
    }
  }, [projectId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const createVersion = useCallback(
    async (data: CreateVersionData) => {
      if (!projectId) {
        throw new Error('No hay un proyecto seleccionado');
      }
      const res = await projectsApi.createVersion(projectId, { name: data.name, parentVersion: data.parentVersion });
      await reload();
      return res.data.data?.id ?? '';
    },
    [projectId, reload],
  );

  const deleteVersion = useCallback(
    async (versionId: string) => {
      await projectsApi.deleteVersion(versionId);
      await reload();
    },
    [reload],
  );

  const mergeVersion = useCallback(
    async (subId: string) => {
      await projectsApi.mergeVersion(subId);
      await reload();
    },
    [reload],
  );

  return {
    versions,
    loading,
    error,
    errorStatus,
    reload,
    createVersion,
    deleteVersion,
    mergeVersion,
  };
}
