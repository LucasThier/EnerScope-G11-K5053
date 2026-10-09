import { useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { organizationsApi } from '../../api/organizations';
import { getErrorMessage } from '../../api/errors';
import type { OrganizationSummary } from '../../types/auth';

interface DeactivateOrganizationDialogProps {
  organization: OrganizationSummary | null;
  onClose: () => void;
  onDeactivated: () => Promise<void>;
}

export function DeactivateOrganizationDialog({
  organization,
  onClose,
  onDeactivated,
}: DeactivateOrganizationDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (organization) {
      setError(null);
    }
  }, [organization]);

  async function handleConfirm() {
    if (!organization) {
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await organizationsApi.deactivate(organization.id);
      await onDeactivated();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo dar de baja la organización'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={organization !== null} onClose={onClose} title="Dar de baja la organización">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        <p className="text-sm text-ink-700">
          Se va a dar de baja a{' '}
          <span className="font-semibold text-ink-800">{organization?.name}</span>.
        </p>
        <p className="text-sm text-ink-700">
          Sus proyectos y sus integrantes dejan de estar accesibles, y no se podrá crear
          nada dentro de ella: ni proyectos, ni integrantes, ni cuentas nuevas.
        </p>
        <p className="text-sm text-ink-500">
          Nada se borra. Los proyectos y las membresías conservan su propio estado, así que
          reactivarla devuelve exactamente lo que había.
        </p>

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={handleConfirm} loading={submitting}>
            Dar de baja
          </Button>
        </div>
      </div>
    </Modal>
  );
}
