import { useEffect, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { organizationsApi } from '../../api/organizations';
import { getErrorMessage } from '../../api/errors';
import type { OrganizationSummary } from '../../types/auth';

interface EditOrganizationModalProps {
  organization: OrganizationSummary | null;
  onClose: () => void;
  onUpdated: () => Promise<void>;
}

const NAME_MIN = 2;
const NAME_MAX = 120;

export function EditOrganizationModal({
  organization,
  onClose,
  onUpdated,
}: EditOrganizationModalProps) {
  const [name, setName] = useState('');
  const [fieldError, setFieldError] = useState<string | undefined>(undefined);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (organization) {
      setName(organization.name);
      setFieldError(undefined);
      setSubmitError(null);
    }
  }, [organization]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!organization) {
      return;
    }
    setSubmitError(null);

    const trimmed = name.trim();
    if (trimmed.length < NAME_MIN || trimmed.length > NAME_MAX) {
      setFieldError(`El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`);
      return;
    }
    setFieldError(undefined);

    if (trimmed === organization.name) {
      onClose();
      return;
    }

    setSubmitting(true);
    try {
      await organizationsApi.update(organization.id, { name: trimmed });
      await onUpdated();
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo renombrar la organización'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={organization !== null} onClose={onClose} title="Renombrar organización">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldError}
          maxLength={NAME_MAX}
        />

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Guardar cambios
          </Button>
        </div>
      </form>
    </Modal>
  );
}
