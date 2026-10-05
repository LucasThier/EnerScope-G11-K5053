import { useEffect, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { TextArea } from '../ui/TextArea';
import { TextField } from '../ui/TextField';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import type { ProjectSummary, UpdateProjectRequest } from '../../types/project';

interface EditProjectModalProps {
  project: ProjectSummary | null;
  onClose: () => void;
  onUpdated: () => Promise<void>;
}

const NAME_MIN = 2;
const NAME_MAX = 120;
const DESCRIPTION_MAX = 500;

interface FieldErrors {
  name?: string;
  description?: string;
}

export function EditProjectModal({ project, onClose, onUpdated }: EditProjectModalProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (project) {
      setName(project.name);
      setDescription(project.description);
      setFieldErrors({});
      setSubmitError(null);
    }
  }, [project]);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    const trimmedName = name.trim();
    if (trimmedName.length < NAME_MIN || trimmedName.length > NAME_MAX) {
      errors.name = `El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
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
    if (!project) {
      return;
    }
    setSubmitError(null);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    const changes: UpdateProjectRequest = {};
    const trimmedName = name.trim();
    const trimmedDescription = description.trim();
    if (trimmedName !== project.name) {
      changes.name = trimmedName;
    }
    if (trimmedDescription !== project.description) {
      changes.description = trimmedDescription;
    }
    if (Object.keys(changes).length === 0) {
      onClose();
      return;
    }

    setSubmitting(true);
    try {
      await projectsApi.update(project.id, changes);
      await onUpdated();
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo guardar el proyecto'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={project !== null} onClose={onClose} title="Editar proyecto">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <TextField
          label="Nombre del proyecto"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={fieldErrors.name}
          maxLength={NAME_MAX}
        />

        <TextArea
          label="Descripción"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          error={fieldErrors.description}
          maxLength={DESCRIPTION_MAX}
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
