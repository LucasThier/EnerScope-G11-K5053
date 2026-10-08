import { useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import type { ProjectSummary } from '../../types/project';

interface DeleteProjectDialogProps {
  project: ProjectSummary | null;
  onClose: () => void;
  onDeleted: () => Promise<void>;
}

export function DeleteProjectDialog({ project, onClose, onDeleted }: DeleteProjectDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    if (project) {
      setError(null);
    }
  }, [project]);

  async function handleConfirm() {
    if (!project) {
      return;
    }
    setError(null);
    setDeleting(true);
    try {
      await projectsApi.remove(project.id);
      await onDeleted();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo eliminar el proyecto'));
    } finally {
      setDeleting(false);
    }
  }

  return (
    <Modal open={project !== null} onClose={onClose} title="Eliminar proyecto">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        <p className="text-sm text-ink-700">
          Se va a eliminar <span className="font-semibold text-ink-800">{project?.name}</span> junto
          con sus integrantes y sus versiones.
        </p>
        <p className="text-sm text-ink-500">
          El proyecto deja de aparecer en el listado, pero no se borra de la base: un administrador
          de plataforma puede recuperarlo.
        </p>

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={deleting}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={handleConfirm} loading={deleting}>
            Eliminar proyecto
          </Button>
        </div>
      </div>
    </Modal>
  );
}
