import { useEffect, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { FilterSelect } from '../ui/FilterSelect';
import { Modal } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { getErrorMessage } from '../../api/errors';
import { useVersions } from '../../hooks/useVersions';

interface NewVersionModalProps {
  projectId: string;
  open: boolean;
  onClose: () => void;
  onCreated: (versionId: string) => Promise<void> | void;
  defaultParentId?: string;
}

const NAME_MAX = 320;

export function NewVersionModal({
  projectId,
  open,
  onClose,
  onCreated,
  defaultParentId,
}: NewVersionModalProps) {
  const { versions, loading: loadingVersions, createVersion } = useVersions(open ? projectId : null);
  const [name, setName] = useState('');
  const [parentVersionId, setParentVersionId] = useState('');
  const [nameError, setNameError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!open) {
      setName('');
      setNameError(null);
      setSubmitError(null);
    }
  }, [open]);

  useEffect(() => {
    if (!open || loadingVersions || versions.length === 0) {
      return;
    }
    if (defaultParentId && versions.some((version) => version.id === defaultParentId)) {
      setParentVersionId(defaultParentId);
      return;
    }
    const root = versions.find((version) => version.parentVersionId === null) ?? versions[0];
    setParentVersionId(root.id);
  }, [open, loadingVersions, versions, defaultParentId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);
    const trimmedName = name.trim();
    if (!trimmedName) {
      setNameError('El nombre es obligatorio.');
      return;
    }
    setNameError(null);

    setSubmitting(true);
    try {
      const createdId = await createVersion({
        name: trimmedName,
        parentVersion: versions.length > 0 ? parentVersionId : null,
      });
      await onCreated(createdId);
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo crear la versión'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={open} onClose={onClose} title="Nueva versión">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {submitError && <Alert tone="error">{submitError}</Alert>}

        <TextField
          label="Nombre"
          value={name}
          onChange={(e) => setName(e.target.value)}
          error={nameError ?? undefined}
          placeholder="Escenario base 2026"
          maxLength={NAME_MAX}
          disabled={submitting}
        />

        {!loadingVersions && versions.length > 0 && (
          <div className="flex flex-col gap-2">
            <span className="text-sm font-medium text-ink-600">Versión padre</span>
            <FilterSelect
              label="Versión padre"
              value={parentVersionId}
              options={versions.map((version) => ({ value: version.id, label: version.name }))}
              onChange={setParentVersionId}
            />
          </div>
        )}

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Crear versión
          </Button>
        </div>
      </form>
    </Modal>
  );
}
