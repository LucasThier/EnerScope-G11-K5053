import { useEffect, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { getErrorMessage } from '../../api/errors';

interface NewOrganizationModalProps {
  open: boolean;
  onClose: () => void;
  onCreate: (name: string) => Promise<unknown>;
}

const NAME_MAX = 120;

export function NewOrganizationModal({ open, onClose, onCreate }: NewOrganizationModalProps) {
  const [name, setName] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setName('');
      setError(null);
    }
  }, [open]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const trimmed = name.trim();
    if (!trimmed) {
      setError('El nombre es obligatorio.');
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await onCreate(trimmed);
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo crear la organización'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Nueva organización">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}

        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Acme Energy"
          maxLength={NAME_MAX}
        />

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Crear organización
          </Button>
        </div>
      </form>
    </Modal>
  );
}
