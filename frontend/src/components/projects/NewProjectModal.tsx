import { useEffect, useId, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextArea } from '../ui/TextArea';
import { TextField } from '../ui/TextField';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { useOrganizations } from '../../hooks/useOrganizations';

interface NewProjectModalProps {
  open: boolean;
  onClose: () => void;
  onCreated: () => Promise<void>;
}

const NAME_MIN = 2;
const NAME_MAX = 120;
const DESCRIPTION_MAX = 500;

const selectClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40 disabled:opacity-60';

interface FieldErrors {
  name?: string;
  organizationId?: string;
  description?: string;
}

export function NewProjectModal({ open, onClose, onCreated }: NewProjectModalProps) {
  const organizationSelectId = useId();
  const { organizations, loading: loadingOrganizations } = useOrganizations();
  const [name, setName] = useState('');
  const [organizationId, setOrganizationId] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setName('');
      setOrganizationId('');
      setDescription('');
      setFieldErrors({});
      setSubmitError(null);
    }
  }, [open]);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    const trimmedName = name.trim();
    if (trimmedName.length < NAME_MIN || trimmedName.length > NAME_MAX) {
      errors.name = `El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    }
    if (!organizationId) {
      errors.organizationId = 'Elegí una organización líder.';
    }
    const trimmedDescription = description.trim();
    if (!trimmedDescription) {
      errors.description = 'La descripción es obligatoria.';
    } else if (trimmedDescription.length > DESCRIPTION_MAX) {
      errors.description = `La descripción no puede superar los ${DESCRIPTION_MAX} caracteres.`;
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await projectsApi.create({
        name: name.trim(),
        description: description.trim(),
        organizationId,
      });
      await onCreated();
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo crear el proyecto'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Nuevo proyecto">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <TextField
          label="Nombre del proyecto"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
          placeholder="Terminal de regasificación"
          maxLength={NAME_MAX}
        />

        <div className="flex flex-col gap-2">
          <label htmlFor={organizationSelectId} className="text-sm font-medium text-ink-600">
            Organización líder
          </label>
          <select
            id={organizationSelectId}
            value={organizationId}
            disabled={loadingOrganizations}
            onChange={(e) => setOrganizationId(e.target.value)}
            aria-invalid={fieldErrors.organizationId ? true : undefined}
            className={
              fieldErrors.organizationId
                ? selectClasses.replace('border-ink-200', 'border-red-400')
                : selectClasses
            }
          >
            <option value="">
              {loadingOrganizations ? 'Cargando organizaciones…' : 'Elegí una organización'}
            </option>
            {organizations.map((organization) => (
              <option key={organization.id} value={organization.id}>
                {organization.name}
              </option>
            ))}
          </select>
          {fieldErrors.organizationId && (
            <span className="text-xs text-red-600">{fieldErrors.organizationId}</span>
          )}
        </div>

        <TextArea
          label="Descripción"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description}
          placeholder="Qué alcance tiene el proyecto"
          maxLength={DESCRIPTION_MAX}
        />

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Crear proyecto
          </Button>
        </div>
      </form>
    </Modal>
  );
}
