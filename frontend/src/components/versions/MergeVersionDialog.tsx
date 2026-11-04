import { useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { Spinner } from '../ui/Spinner';
import { projectsApi } from '../../api/projects';
import { getErrorMessage, getErrorStatus } from '../../api/errors';
import { fetchConflictsByVersion } from '../../hooks/useVersionConflicts';
import { groupPendingConflicts } from '../../utils/conflictGroups';
import { isMergeBlocked, MERGE_BLOCKED_MESSAGE } from '../../utils/versionMerge';
import {
  conflictElementLabel,
  mergedChangeLabel,
  ownChangeLabel,
  withDescription,
} from './conflictLabels';
import type { VersionConflict, VersionSummary } from '../../types/diagram';

interface MergeVersionDialogProps {
  version: VersionSummary | null;
  parent: VersionSummary | null;
  siblings: VersionSummary[];
  onMerge: (subId: string) => Promise<void>;
  onRefresh: () => Promise<void>;
  onResolveConflicts: (versionId: string) => void;
  onClose: () => void;
}

interface AffectedVersionGroup {
  versionId: string;
  versionName: string;
  conflicts: VersionConflict[];
}

const CONFLICT_CHECK_ERROR = 'No se pudo verificar si hay conflictos pendientes. Intentá de nuevo.';

function conflictsFromMerge(
  byVersion: Map<string, VersionConflict[]>,
  sourceVersionId: string,
  mergedVersionId: string | null,
): VersionConflict[] {
  const fromMerge: VersionConflict[] = [];
  byVersion.forEach((conflicts) => {
    for (const conflict of conflicts) {
      const matchesMerge =
        !conflict.resolved &&
        conflict.sourceVersionId === sourceVersionId &&
        (mergedVersionId === null || conflict.mergedVersionId === mergedVersionId);
      if (matchesMerge) {
        fromMerge.push(conflict);
      }
    }
  });
  return fromMerge;
}

function groupByAffectedVersion(conflicts: VersionConflict[]): AffectedVersionGroup[] {
  const groups = new Map<string, AffectedVersionGroup>();
  for (const conflict of conflicts) {
    const group = groups.get(conflict.conflictingVersionId) ?? {
      versionId: conflict.conflictingVersionId,
      versionName: conflict.conflictingVersionName,
      conflicts: [],
    };
    group.conflicts.push(conflict);
    groups.set(conflict.conflictingVersionId, group);
  }
  return [...groups.values()];
}

export function MergeVersionDialog({
  version,
  parent,
  siblings,
  onMerge,
  onRefresh,
  onResolveConflicts,
  onClose,
}: MergeVersionDialogProps) {
  const versionId = version?.id ?? null;
  const [merged, setMerged] = useState(false);
  const [merging, setMerging] = useState(false);
  const [checking, setChecking] = useState(false);
  const [checkFailed, setCheckFailed] = useState(false);
  const [checkToken, setCheckToken] = useState(0);
  const [pending, setPending] = useState(0);
  const [loadingConflicts, setLoadingConflicts] = useState(false);
  const [conflicts, setConflicts] = useState<VersionConflict[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!versionId) {
      return;
    }
    let cancelled = false;
    setMerged(false);
    setConflicts([]);
    setError(null);
    setPending(0);
    setCheckFailed(false);
    setChecking(true);

    projectsApi
      .getVersionConflicts(versionId)
      .then((res) => {
        if (!cancelled) {
          setPending(groupPendingConflicts(res.data.data ?? []).length);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCheckFailed(true);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setChecking(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [versionId, checkToken]);

  async function handleConfirm() {
    if (!version || checking || checkFailed || isMergeBlocked(pending)) {
      return;
    }
    setError(null);
    setMerging(true);
    try {
      await onMerge(version.id);
    } catch (err) {
      if (getErrorStatus(err) === 404) {
        await onRefresh();
        onClose();
        return;
      }
      setError(getErrorMessage(err, 'No se pudo mergear la versión'));
      return;
    } finally {
      setMerging(false);
    }

    setMerged(true);
    if (siblings.length === 0) {
      return;
    }

    setLoadingConflicts(true);
    const byVersion = await fetchConflictsByVersion(siblings.map((sibling) => sibling.id));
    setConflicts(conflictsFromMerge(byVersion, version.id, parent?.id ?? null));
    setLoadingConflicts(false);
  }

  const groups = groupByAffectedVersion(conflicts).map((group) => ({
    ...group,
    items: groupPendingConflicts(group.conflicts),
  }));
  const totalItems = groups.reduce((sum, group) => sum + group.items.length, 0);
  const blocked = !merged && isMergeBlocked(pending);

  return (
    <Modal
      open={version !== null}
      onClose={onClose}
      title={merged ? 'Resultado del merge' : 'Mergear versión'}
    >
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        {merged ? (
          <>
            <p className="text-sm text-ink-700">
              Se mergeó <span className="font-semibold text-ink-800">{version?.name}</span> en{' '}
              <span className="font-semibold text-ink-800">{parent?.name}</span>.
            </p>

            {loadingConflicts ? (
              <div className="flex items-center gap-2 text-sm text-ink-500">
                <Spinner className="h-4 w-4" />
                Consultando conflictos…
              </div>
            ) : groups.length === 0 ? (
              <p className="text-sm text-ink-500">Sin conflictos detectados.</p>
            ) : (
              <div className="flex flex-col gap-4">
                <p className="text-sm font-medium text-ink-600">
                  {totalItems === 1
                    ? 'Se detectó 1 conflicto en las versiones derivadas:'
                    : `Se detectaron ${totalItems} conflictos en las versiones derivadas:`}
                </p>
                {groups.map((group) => (
                  <div key={group.versionId} className="flex flex-col gap-2">
                    <div className="flex items-center justify-between gap-3">
                      <p className="text-sm font-semibold text-ink-800">
                        {group.versionName} ({group.items.length})
                      </p>
                      <Button
                        type="button"
                        variant="ghost"
                        className="px-2 py-1 text-xs"
                        onClick={() => onResolveConflicts(group.versionId)}
                      >
                        Resolver
                      </Button>
                    </div>
                    <ul className="flex flex-col gap-3 border-l-2 border-ink-100 pl-3">
                      {group.items.map((conflictGroup) => (
                        <ConflictItem
                          key={conflictGroup.key}
                          conflict={conflictGroup.conflicts[0]}
                          count={conflictGroup.conflicts.length}
                          parentName={parent?.name ?? 'el padre'}
                        />
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}

            <div className="mt-2 flex justify-end">
              <Button type="button" onClick={onClose}>
                Cerrar
              </Button>
            </div>
          </>
        ) : checking ? (
          <div className="flex items-center gap-2 text-sm text-ink-500">
            <Spinner className="h-4 w-4" />
            Revisando conflictos…
          </div>
        ) : checkFailed ? (
          <>
            <Alert tone="error">{CONFLICT_CHECK_ERROR}</Alert>
            <ModalFooter>
              <Button type="button" variant="ghost" onClick={onClose}>
                Cerrar
              </Button>
              <Button type="button" onClick={() => setCheckToken((token) => token + 1)}>
                Reintentar
              </Button>
            </ModalFooter>
          </>
        ) : blocked ? (
          <>
            <p className="text-sm text-ink-700">
              <span className="font-semibold text-ink-800">{version?.name}</span> tiene{' '}
              {pending === 1 ? '1 conflicto pendiente' : `${pending} conflictos pendientes`}.
            </p>
            <Alert tone="error">{MERGE_BLOCKED_MESSAGE}.</Alert>

            <ModalFooter>
              <Button type="button" variant="ghost" onClick={onClose}>
                Cerrar
              </Button>
              {versionId && (
                <Button type="button" onClick={() => onResolveConflicts(versionId)}>
                  Resolver conflictos
                </Button>
              )}
            </ModalFooter>
          </>
        ) : (
          <>
            <p className="text-sm text-ink-700">
              Los cambios de <span className="font-semibold text-ink-800">{version?.name}</span> se
              aplican sobre <span className="font-semibold text-ink-800">{parent?.name}</span>.
            </p>
            <p className="text-sm text-ink-500">
              Otras versiones derivadas de{' '}
              <span className="font-semibold text-ink-700">{parent?.name}</span> pueden quedar con
              conflictos para revisar.
            </p>

            <Alert tone="error">Esta acción no se puede deshacer.</Alert>

            <ModalFooter>
              <Button type="button" variant="ghost" onClick={onClose} disabled={merging}>
                Cancelar
              </Button>
              <Button type="button" onClick={handleConfirm} loading={merging}>
                Mergear
              </Button>
            </ModalFooter>
          </>
        )}
      </div>
    </Modal>
  );
}

function ConflictItem({
  conflict,
  count,
  parentName,
}: {
  conflict: VersionConflict;
  count: number;
  parentName: string;
}) {
  return (
    <li className="flex flex-col gap-0.5">
      <span className="text-sm font-medium text-ink-800">{conflictElementLabel(conflict)}</span>
      <span className="text-sm text-ink-500">
        En {parentName}:{' '}
        {withDescription(mergedChangeLabel(conflict.sourceChangeType), conflict.mergedDescription)}
      </span>
      <span className="text-sm text-ink-500">
        En {conflict.conflictingVersionName}:{' '}
        {withDescription(
          ownChangeLabel(conflict.conflictingChangeType),
          conflict.conflictingDescription,
        )}
      </span>
      {count > 1 && (
        <span className="text-xs text-ink-500">{count} registros para este elemento.</span>
      )}
    </li>
  );
}
