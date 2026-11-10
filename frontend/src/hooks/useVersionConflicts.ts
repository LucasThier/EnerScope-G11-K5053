import { useCallback, useEffect, useMemo, useState } from 'react';
import { projectsApi } from '../api/projects';
import { groupPendingConflicts } from '../utils/conflictGroups';
import { getNonRootVersionIds } from '../utils/versionTree';
import type { VersionConflict, VersionSummary } from '../types/diagram';

const BATCH_SIZE = 5;

interface UseVersionConflicts {
  unresolvedByVersion: Record<string, number>;
  reload: () => Promise<void>;
  reloadVersion: (versionId: string) => Promise<void>;
}

function countUnresolvedGroups(conflicts: VersionConflict[]): number {
  return groupPendingConflicts(conflicts).length;
}

export async function fetchConflictsByVersion(
  versionIds: string[],
): Promise<Map<string, VersionConflict[]>> {
  const byVersion = new Map<string, VersionConflict[]>();

  for (let start = 0; start < versionIds.length; start += BATCH_SIZE) {
    const batch = versionIds.slice(start, start + BATCH_SIZE);
    const settled = await Promise.allSettled(
      batch.map((versionId) => projectsApi.getVersionConflicts(versionId)),
    );
    settled.forEach((outcome, index) => {
      if (outcome.status === 'fulfilled') {
        byVersion.set(batch[index], outcome.value.data.data ?? []);
      }
    });
  }

  return byVersion;
}

export function useVersionConflicts(versions: VersionSummary[]): UseVersionConflicts {
  const [unresolvedByVersion, setUnresolvedByVersion] = useState<Record<string, number>>({});
  const [refreshToken, setRefreshToken] = useState(0);

  const versionIds = useMemo(() => getNonRootVersionIds(versions), [versions]);
  const versionIdsKey = versionIds.join(',');

  useEffect(() => {
    let cancelled = false;
    const ids = versionIdsKey ? versionIdsKey.split(',') : [];

    if (ids.length === 0) {
      setUnresolvedByVersion({});
      return;
    }

    void fetchConflictsByVersion(ids).then((byVersion) => {
      if (cancelled) {
        return;
      }
      const counts: Record<string, number> = {};
      byVersion.forEach((conflicts, versionId) => {
        const unresolved = countUnresolvedGroups(conflicts);
        if (unresolved > 0) {
          counts[versionId] = unresolved;
        }
      });
      setUnresolvedByVersion(counts);
    });

    return () => {
      cancelled = true;
    };
  }, [versionIdsKey, refreshToken]);

  const reload = useCallback(async () => {
    setRefreshToken((token) => token + 1);
  }, []);

  const reloadVersion = useCallback(async (versionId: string) => {
    const byVersion = await fetchConflictsByVersion([versionId]);
    const conflicts = byVersion.get(versionId);
    if (!conflicts) {
      return;
    }
    const unresolved = countUnresolvedGroups(conflicts);
    setUnresolvedByVersion((current) => {
      const next = { ...current };
      if (unresolved > 0) {
        next[versionId] = unresolved;
      } else {
        delete next[versionId];
      }
      return next;
    });
  }, []);

  return { unresolvedByVersion, reload, reloadVersion };
}
