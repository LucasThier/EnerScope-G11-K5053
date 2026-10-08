import { useState, type ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { getErrorMessage } from '../../api/errors';

interface RemoveMemberDialogProps {
  open: boolean;
  title: string;
  description: ReactNode;
  warning?: ReactNode;
  onClose: () => void;
  onConfirm: () => Promise<void>;
}

export function RemoveMemberDialog({
  open,
  title,
  description,
  warning,
  onClose,
  onConfirm,
}: RemoveMemberDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [wasOpen, setWasOpen] = useState(open);

  if (open !== wasOpen) {
    setWasOpen(open);
    if (open) {
      setError(null);
    }
  }

  async function handleConfirm() {
    setError(null);
    setSubmitting(true);
    try {
      await onConfirm();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo quitar al integrante'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={title}>
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        {description}

        {warning && <Alert tone="error">{warning}</Alert>}

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={handleConfirm} loading={submitting}>
            Quitar integrante
          </Button>
        </div>
      </div>
    </Modal>
  );
}
