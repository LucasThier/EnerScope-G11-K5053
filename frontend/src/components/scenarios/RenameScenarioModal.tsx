import { useEffect, useState, type FormEvent } from 'react';
import { versionsApi } from '../../api/versions';
import { getErrorMessage } from '../../api/errors';
import type { VersionSummary } from '../../types/diagram';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';

interface RenameScenarioModalProps {
  version: VersionSummary | null;
  onClose: () => void;
  onRenamed: () => void;
}

export function RenameScenarioModal({ version, onClose, onRenamed }: RenameScenarioModalProps) {
  const [name, setName] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setName(version?.name ?? '');
    setError(null);
  }, [version]);

  const trimmed = name.trim();

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!version || trimmed.length < 2) return;
    if (trimmed === version.name) {
      onClose();
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await versionsApi.rename(version.id, trimmed);
      onRenamed();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo renombrar el escenario'));
    } finally {
      setSaving(false);
    }
  }

  return (
    <Modal open={version !== null} onClose={onClose} title="Renombrar escenario" subtitle={version?.name}>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={120}
          error={trimmed.length > 0 && trimmed.length < 2 ? 'Usá al menos 2 caracteres.' : undefined}
        />
        {error && <Alert tone="error">{error}</Alert>}
        <div className="flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            Cancelar
          </Button>
          <Button type="submit" loading={saving} disabled={trimmed.length < 2}>
            Guardar
          </Button>
        </div>
      </form>
    </Modal>
  );
}
