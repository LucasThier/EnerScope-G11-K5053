import { useState } from 'react';
import { versionsApi } from '../../api/versions';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

interface DeleteScenarioDialogProps {
  version: VersionSummary | null;
  /** Branches that go with it: the backend deletes the whole subtree. */
  descendants: VersionSummary[];
  onClose: () => void;
  onDeleted: () => void;
}

/**
 * Deleting a scenario is physical and takes every branch below it along
 * (`VersionService.deleteVersion` walks the tree), so the dialog names them.
 */
export function DeleteScenarioDialog({ version, descendants, onClose, onDeleted }: DeleteScenarioDialogProps) {
  const [deleting, setDeleting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    if (!version) return;
    setDeleting(true);
    setError(null);
    try {
      await versionsApi.remove(version.id);
      onDeleted();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo eliminar el escenario'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Modal open={version !== null} onClose={onClose} title="Eliminar escenario" subtitle={version?.name}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-700">
          Se elimina el escenario con su modelo y sus simulaciones. No se puede deshacer.
        </p>
        {descendants.length > 0 && (
          <Alert tone="error">
            También se eliminan {descendants.length === 1 ? 'el escenario' : `los ${descendants.length} escenarios`}{' '}
            que parten de él: {descendants.map((d) => d.name).join(', ')}.
          </Alert>
        )}
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button variant="danger" loading={deleting} onClick={confirm}>
            Eliminar
          </Button>
        </div>
      </div>
    </Modal>
  );
}
