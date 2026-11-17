import { useCallback, useEffect, useRef, useState } from 'react';
import { versionAccessApi } from '../api/versionAccess';
import { getVersionRoleErrorMessage } from '../api/errors';
import type { VersionMember } from '../types/versionAccess';

interface UseVersionMembers {
  members: VersionMember[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

export function useVersionMembers(versionId: string | null): UseVersionMembers {
  const [members, setMembers] = useState<VersionMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const latestVersion = useRef<string | null>(versionId);

  const load = useCallback(async (id: string) => {
    try {
      const res = await versionAccessApi.members(id);
      if (latestVersion.current !== id) {
        return;
      }
      setMembers(res.data.data ?? []);
      setError(null);
    } catch (err) {
      if (latestVersion.current !== id) {
        return;
      }
      setMembers([]);
      setError(getVersionRoleErrorMessage(err, 'No se pudieron cargar los roles de la versión'));
    }
    setLoadedFor(id);
  }, []);

  useEffect(() => {
    latestVersion.current = versionId;
    if (versionId) {
      void load(versionId);
    }
  }, [versionId, load]);

  const reload = useCallback(async () => {
    if (versionId) {
      await load(versionId);
    }
  }, [versionId, load]);

  const current = versionId !== null && loadedFor === versionId;
  return {
    members: current ? members : [],
    loading: versionId !== null && !current,
    error: current ? error : null,
    reload,
  };
}
