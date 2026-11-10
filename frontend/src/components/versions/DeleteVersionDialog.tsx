import { useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { getErrorMessage, getErrorStatus } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';

interface DeleteVersionDialogProps {
  version: VersionSummary | null;
  descendants: VersionSummary[];
  isRoot: boolean;
  onDelete: (versionId: string) => Promise<void>;
  onRefresh: () => Promise<void>;
  onClose: () => void;
}

const MAX_LISTED = 10;

export function DeleteVersionDialog({
  version,
  descendants,
  isRoot,
  onDelete,
  onRefresh,
  onClose,
}: DeleteVersionDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (version) {
      setError(null);
    }
  }, [version]);

  async function handleConfirm() {
    if (!version) {
      return;
    }
    setError(null);
    setDeleting(true);
    try {
      await onDelete(version.id);
      onClose();
    } catch (err) {
      if (getErrorStatus(err) === 404) {
        await onRefresh();
        onClose();
        return;
      }
      setError(getErrorMessage(err, 'No se pudo eliminar la versión'));
    } finally {
      setDeleting(false);
    }
  }

  const listed = descendants.slice(0, MAX_LISTED);
  const remaining = descendants.length - listed.length;

  return (
    <Modal open={version !== null} onClose={onClose} title="Eliminar versión">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        <p className="text-sm text-ink-700">
          Se va a eliminar la versión <span className="font-semibold text-ink-800">{version?.name}</span>
          {descendants.length > 0 ? ' y todo lo que deriva de ella.' : '.'}
        </p>

        {isRoot && (
          <p className="text-sm text-ink-500">
            {descendants.length > 0
              ? 'Es la versión base del proyecto: se elimina junto con todas sus versiones derivadas.'
              : 'Es la versión base del proyecto, así que el proyecto queda sin versiones.'}
          </p>
        )}

        {descendants.length > 0 && (
          <div className="flex flex-col gap-2">
            <p className="text-sm font-medium text-ink-600">
              {descendants.length === 1
                ? 'Versión derivada que se elimina:'
                : `Versiones derivadas que se eliminan (${descendants.length}):`}
            </p>
            <ul className="list-disc pl-5 text-sm text-ink-700">
              {listed.map((descendant) => (
                <li key={descendant.id}>{descendant.name}</li>
              ))}
            </ul>
            {remaining > 0 && <p className="text-sm text-ink-500">y {remaining} más</p>}
          </div>
        )}

        <Alert tone="error">
          Esta acción no se puede deshacer: las versiones y sus datos se borran definitivamente.
        </Alert>

        <ModalFooter>
          <Button type="button" variant="ghost" onClick={onClose} disabled={deleting}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={handleConfirm} loading={deleting}>
            Eliminar versión
          </Button>
        </ModalFooter>
      </div>
    </Modal>
  );
}
