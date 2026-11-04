import { useCallback, useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { Spinner } from '../ui/Spinner';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { groupPendingConflicts } from '../../utils/conflictGroups';
import {
  conflictElementLabel,
  mergedChangeLabel,
  ownChangeLabel,
  withDescription,
} from './conflictLabels';
import type { ConflictDecision, VersionSummary } from '../../types/diagram';
import type { ConflictGroup } from '../../utils/conflictGroups';

interface ResolveConflictsDialogProps {
  version: VersionSummary | null;
  parent: VersionSummary | null;
  canEdit: boolean;
  onResolved: (versionId: string) => Promise<void>;
  onMergeNow: (version: VersionSummary) => void;
  onClose: () => void;
}

const DECISION_OPTIONS: { decision: ConflictDecision; label: string; detail: string }[] = [
  {
    decision: 'ACCEPT',
    label: 'Conservar el cambio del padre',
    detail: 'Esta versión queda igual que el padre y descarta su propio cambio.',
  },
  {
    decision: 'REJECT',
    label: 'Conservar el de esta versión',
    detail: 'Esta versión mantiene lo suyo y la diferencia queda registrada como cambio propio.',
  },
];

export function ResolveConflictsDialog({
  version,
  parent,
  canEdit,
  onResolved,
  onMergeNow,
  onClose,
}: ResolveConflictsDialogProps) {
  const versionId = version?.id ?? null;
  const [items, setItems] = useState<ConflictGroup[]>([]);
  const [decisions, setDecisions] = useState<Record<string, ConflictDecision>>({});
  const [failures, setFailures] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [applying, setApplying] = useState(false);

  const load = useCallback(async () => {
    if (!versionId) {
      return;
    }
    setLoading(true);
    setLoadError(null);
    try {
      const res = await projectsApi.getVersionConflicts(versionId);
      setItems(groupPendingConflicts(res.data.data ?? []));
    } catch (err) {
      setLoadError(getErrorMessage(err, 'No se pudieron cargar los conflictos'));
    } finally {
      setLoading(false);
    }
  }, [versionId]);

  useEffect(() => {
    if (!versionId) {
      return;
    }
    setItems([]);
    setDecisions({});
    setFailures({});
    void load();
  }, [versionId, load]);

  async function handleApply() {
    if (!versionId) {
      return;
    }
    setApplying(true);
    const nextFailures: Record<string, string> = {};

    for (const item of items) {
      const decision = decisions[item.key];
      if (!decision) {
        continue;
      }
      try {
        for (const conflict of item.conflicts) {
          await projectsApi.resolveConflict(conflict.id, decision);
        }
      } catch (err) {
        nextFailures[item.key] = getErrorMessage(err, 'No se pudo resolver el conflicto');
      }
    }

    setFailures(nextFailures);
    await onResolved(versionId);
    await load();
    setApplying(false);
  }

  const allDecided = items.length > 0 && items.every((item) => decisions[item.key] !== undefined);
  const parentName = parent?.name ?? 'el padre';

  return (
    <Modal
      open={version !== null}
      onClose={onClose}
      title={`Conflictos de «${version?.name ?? ''}»`}
    >
      <div className="flex flex-col gap-4">
        {loadError && <Alert tone="error">{loadError}</Alert>}

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-ink-500">
            <Spinner className="h-4 w-4" />
            Cargando conflictos…
          </div>
        ) : loadError ? null : items.length === 0 ? (
          <p className="text-sm text-ink-500">No hay conflictos pendientes.</p>
        ) : (
          <>
            <p className="text-sm text-ink-700">
              <span className="font-semibold text-ink-800">{parentName}</span> recibió cambios de
              otra versión derivada sobre elementos que{' '}
              <span className="font-semibold text-ink-800">{version?.name}</span> también tiene.
            </p>
            {canEdit && (
              <p className="text-sm text-ink-500">
                Elegí qué dato queda en esta versión. El padre no se modifica.
              </p>
            )}

            <div className="flex flex-col gap-3">
              {items.map((item) => {
                const conflict = item.conflicts[0];
                return (
                  <fieldset key={item.key} className="rounded-lg border border-ink-100 p-3">
                    <legend className="px-1 text-sm font-medium text-ink-800">
                      {conflictElementLabel(conflict)}
                    </legend>

                    <div className="flex flex-col gap-0.5">
                      <span className="text-sm text-ink-500">
                        En {parentName}:{' '}
                        {withDescription(
                          mergedChangeLabel(conflict.sourceChangeType),
                          conflict.mergedDescription,
                        )}
                      </span>
                      <span className="text-sm text-ink-500">
                        En esta versión:{' '}
                        {withDescription(
                          ownChangeLabel(conflict.conflictingChangeType),
                          conflict.conflictingDescription,
                        )}
                      </span>
                      {item.conflicts.length > 1 && (
                        <span className="text-xs text-ink-500">
                          {item.conflicts.length} registros para este elemento: la decisión se
                          aplica a todos.
                        </span>
                      )}
                    </div>

                    {canEdit && (
                      <div className="mt-3 flex flex-col gap-2">
                        {DECISION_OPTIONS.map((option) => (
                          <label
                            key={option.decision}
                            className="flex items-start gap-2 text-sm text-ink-700"
                          >
                            <input
                              type="radio"
                              name={item.key}
                              value={option.decision}
                              checked={decisions[item.key] === option.decision}
                              disabled={applying}
                              onChange={() =>
                                setDecisions((current) => ({
                                  ...current,
                                  [item.key]: option.decision,
                                }))
                              }
                              className="mt-0.5 accent-brand-800"
                            />
                            <span>
                              <span className="font-medium text-ink-800">{option.label}</span>
                              <span className="block text-ink-500">{option.detail}</span>
                            </span>
                          </label>
                        ))}
                      </div>
                    )}

                    {failures[item.key] && (
                      <Alert tone="error" className="mt-3">
                        {failures[item.key]}
                      </Alert>
                    )}
                  </fieldset>
                );
              })}
            </div>
          </>
        )}

        <ModalFooter>
          {items.length === 0 && canEdit && version && parent && !loading && !loadError ? (
            <>
              <Button type="button" variant="ghost" onClick={onClose}>
                Cerrar
              </Button>
              <Button type="button" onClick={() => onMergeNow(version)}>
                Mergear ahora
              </Button>
            </>
          ) : canEdit && items.length > 0 ? (
            <>
              <Button type="button" variant="ghost" onClick={onClose} disabled={applying}>
                Cancelar
              </Button>
              <Button
                type="button"
                onClick={handleApply}
                loading={applying}
                disabled={!allDecided}
              >
                Aplicar resoluciones
              </Button>
            </>
          ) : (
            <Button type="button" variant="ghost" onClick={onClose}>
              Cerrar
            </Button>
          )}
        </ModalFooter>
      </div>
    </Modal>
  );
}
