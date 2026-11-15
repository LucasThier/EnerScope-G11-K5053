import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { FilterSelect } from '../ui/FilterSelect';
import { Modal, ModalFooter } from '../ui/Modal';
import { TextField } from '../ui/TextField';
import { getErrorMessage } from '../../api/errors';
import { useVersions } from '../../hooks/useVersions';
import {
  FIRST_VERSION_ADMIN_MESSAGE,
  NO_EDITABLE_PARENT_MESSAGE,
  newVersionBlockMessage,
  newVersionMode,
  parentForNewVersion,
  pickDefaultParent,
  validateVersionName,
} from '../../utils/newVersion';

interface NewVersionModalProps {
  projectId: string;
  open: boolean;
  onClose: () => void;
  onCreated: (versionId: string) => Promise<void> | void;
  defaultParentId?: string;
  canCreateRoot: boolean;
  canBranchFrom?: (parentVersionId: string) => boolean;
}

const NAME_MAX = 320;

export { FIRST_VERSION_ADMIN_MESSAGE, NO_EDITABLE_PARENT_MESSAGE };

export function NewVersionModal({
  projectId,
  open,
  onClose,
  onCreated,
  defaultParentId,
  canCreateRoot,
  canBranchFrom,
}: NewVersionModalProps) {
  const { versions, loading: loadingVersions, createVersion } = useVersions(open ? projectId : null);
  const parents = useMemo(
    () => (canBranchFrom ? versions.filter((version) => canBranchFrom(version.id)) : versions),
    [versions, canBranchFrom],
  );
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
    if (!open || loadingVersions) {
      return;
    }
    const picked = pickDefaultParent(parents, defaultParentId);
    if (picked) {
      setParentVersionId(picked);
    }
  }, [open, loadingVersions, parents, defaultParentId]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);
    const checked = validateVersionName(name);
    if (checked.name === null) {
      setNameError(checked.error);
      return;
    }
    setNameError(null);

    setSubmitting(true);
    try {
      const createdId = await createVersion({
        name: checked.name,
        parentVersion: parentForNewVersion(versions.length, parentVersionId),
      });
      await onCreated(createdId);
      onClose();
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo crear la versión'));
    } finally {
      setSubmitting(false);
    }
  }

  const mode = newVersionMode({
    loading: loadingVersions,
    versionCount: versions.length,
    parentCount: parents.length,
    canCreateRoot,
  });
  const blockMessage = newVersionBlockMessage(mode);

  return (
    <Modal open={open} onClose={onClose} title="Nueva versión">
      {blockMessage ? (
        <div className="flex flex-col gap-4">
          <Alert tone="info">{blockMessage}</Alert>
          <ModalFooter>
            <Button type="button" variant="ghost" onClick={onClose}>
              Cerrar
            </Button>
          </ModalFooter>
        </div>
      ) : (
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
              options={parents.map((version) => ({ value: version.id, label: version.name }))}
              onChange={setParentVersionId}
            />
          </div>
        )}

        <ModalFooter>
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Crear versión
          </Button>
        </ModalFooter>
      </form>
      )}
    </Modal>
  );
}
