import { useCallback, useEffect, useState } from 'react';
import { versionsApi } from '../../api/versions';
import { getErrorMessage } from '../../api/errors';
import type { VersionConflict, VersionSummary } from '../../types/diagram';
import { formatDateTime } from '../../utils/number';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { Spinner } from '../ui/Spinner';

interface ConflictsModalProps {
  version: VersionSummary | null;
  onClose: () => void;
  /** After a resolution the version's model changed, so the caller reloads. */
  onResolved: () => void;
}

const CHANGE_LABELS: Record<string, string> = { ADD: 'agregó', EDIT: 'editó', DELETE: 'eliminó' };

/**
 * Conflicts recorded on a scenario when a sibling branch was merged into their
 * shared parent. Accepting takes the parent's version of the node or
 * connection; rejecting keeps this scenario's own.
 */
export function ConflictsModal({ version, onClose, onResolved }: ConflictsModalProps) {
  const [conflicts, setConflicts] = useState<VersionConflict[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [resolving, setResolving] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!version) return;
    setError(null);
    try {
      const res = await versionsApi.conflicts(version.id);
      setConflicts(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los conflictos'));
      setConflicts([]);
    }
  }, [version]);

  useEffect(() => {
    setConflicts(null);
    void load();
  }, [load]);

  async function resolve(conflict: VersionConflict, decision: 'ACCEPT' | 'REJECT') {
    setResolving(conflict.id);
    setError(null);
    try {
      await versionsApi.resolveConflict(conflict.id, decision);
      await load();
      onResolved();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo resolver el conflicto'));
    } finally {
      setResolving(null);
    }
  }

  const pending = conflicts?.filter((c) => !c.resolved) ?? [];
  const resolved = conflicts?.filter((c) => c.resolved) ?? [];

  return (
    <Modal open={version !== null} onClose={onClose} title="Conflictos" subtitle={version?.name} size="lg">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}
        {conflicts === null ? (
          <div className="flex justify-center py-6 text-brand-800">
            <Spinner className="h-6 w-6" />
          </div>
        ) : pending.length === 0 ? (
          <p className="text-sm text-ink-500">
            Este escenario no tiene conflictos pendientes.
            {resolved.length > 0 && ` Ya se resolvieron ${resolved.length}.`}
          </p>
        ) : (
          <ul className="flex flex-col divide-y divide-ink-100 rounded-lg border border-ink-100">
            {pending.map((conflict) => (
              <li key={conflict.id} className="flex flex-col gap-2 p-3 text-sm">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-semibold text-ink-800">
                    {conflict.entityType === 'NODE' ? 'Nodo' : 'Conexión'}{' '}
                    {conflict.mergedDescription ?? conflict.conflictingDescription ?? ''}
                  </span>
                  <span className="text-xs text-ink-500">{formatDateTime(conflict.mergedAt)}</span>
                </div>
                <dl className="grid grid-cols-1 gap-1 text-xs sm:grid-cols-2">
                  <div>
                    <dt className="text-ink-500">En el escenario padre</dt>
                    <dd className="text-ink-700">{conflict.mergedDescription ?? 'No existe'}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-500">En este escenario</dt>
                    <dd className="text-ink-700">{conflict.conflictingDescription ?? 'No existe'}</dd>
                  </div>
                </dl>
                <p className="text-xs text-ink-500">
                  La rama fusionada lo {CHANGE_LABELS[conflict.sourceChangeType ?? ''] ?? 'cambió'}
                  {conflict.conflictingChangeType
                    ? `; este escenario también lo ${CHANGE_LABELS[conflict.conflictingChangeType]}.`
                    : '; este escenario no lo había tocado, pero quedó desactualizado.'}
                </p>
                <div className="flex gap-2">
                  <Button
                    className="px-3 py-1 text-xs"
                    loading={resolving === conflict.id}
                    onClick={() => resolve(conflict, 'ACCEPT')}
                  >
                    Tomar la del padre
                  </Button>
                  <Button
                    variant="ghost"
                    className="px-3 py-1 text-xs"
                    disabled={resolving === conflict.id}
                    onClick={() => resolve(conflict, 'REJECT')}
                  >
                    Mantener la de este escenario
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        )}
        <p className="text-xs text-ink-500">
          Resolvé primero los conflictos de nodos y después los de sus conexiones, así cada conexión se
          reconecta al nodo correcto.
        </p>
      </div>
    </Modal>
  );
}
