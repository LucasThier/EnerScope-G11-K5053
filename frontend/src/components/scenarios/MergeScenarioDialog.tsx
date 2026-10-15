import { useState } from 'react';
import { versionsApi } from '../../api/versions';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';

interface MergeScenarioDialogProps {
  version: VersionSummary | null;
  parent: VersionSummary | null;
  onClose: () => void;
  onMerged: () => void;
}

/**
 * Applies a branch's changes onto its parent. Other branches of the same parent
 * that now differ from it get conflicts, resolved from their own row.
 */
export function MergeScenarioDialog({ version, parent, onClose, onMerged }: MergeScenarioDialogProps) {
  const [merging, setMerging] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function confirm() {
    if (!version) return;
    setMerging(true);
    setError(null);
    try {
      await versionsApi.mergeIntoParent(version.id);
      onMerged();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo fusionar el escenario'));
    } finally {
      setMerging(false);
    }
  }

  return (
    <Modal open={version !== null} onClose={onClose} title="Fusionar en el escenario padre" subtitle={version?.name}>
      <div className="flex flex-col gap-4">
        <p className="text-sm text-ink-700">
          Los cambios de <strong>{version?.name}</strong> (nodos y conexiones agregados, editados o
          eliminados) se aplican sobre <strong>{parent?.name ?? 'su escenario padre'}</strong>. Lo que el
          padre tenga y esta rama no haya tocado queda como está.
        </p>
        <p className="text-sm text-ink-500">
          Si otra rama del mismo padre tiene una versión distinta de algo que cambió, se registra un
          conflicto en esa rama para que lo resuelvas. Las simulaciones del padre no se recalculan:
          volvé a simularlo para ver el efecto.
        </p>
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button loading={merging} onClick={confirm}>
            Fusionar
          </Button>
        </div>
      </div>
    </Modal>
  );
}
