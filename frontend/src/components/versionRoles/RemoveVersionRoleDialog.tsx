import { useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal, ModalFooter } from '../ui/Modal';
import { getErrorStatus, getVersionRoleErrorMessage } from '../../api/errors';
import { versionRoleLabel } from '../projects/versionRoleLabels';
import { fullName, isLastRole } from '../../utils/versionRoles';
import type { VersionMember, VersionMemberRole } from '../../types/versionAccess';

export interface RoleRemovalTarget {
  member: VersionMember;
  role: VersionMemberRole;
}

interface RemoveVersionRoleDialogProps {
  target: RoleRemovalTarget | null;
  versionName: string;
  fallbackRoleLabel: string | null;
  administersAll: boolean;
  losesOwnAdmin: boolean;
  onClose: () => void;
  onConfirm: (target: RoleRemovalTarget) => Promise<void>;
  onGone: () => Promise<void>;
}

export function RemoveVersionRoleDialog({
  target,
  versionName,
  fallbackRoleLabel,
  administersAll,
  losesOwnAdmin,
  onClose,
  onConfirm,
  onGone,
}: RemoveVersionRoleDialogProps) {
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [shownFor, setShownFor] = useState<string | null>(null);

  const targetKey = target ? target.role.roleId : null;
  if (targetKey !== shownFor) {
    setShownFor(targetKey);
    setError(null);
  }

  async function handleConfirm() {
    if (!target) {
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await onConfirm(target);
    } catch (err) {
      if (getErrorStatus(err) === 404) {
        await onGone();
      } else {
        setError(getVersionRoleErrorMessage(err, 'No se pudo quitar el rol'));
      }
    } finally {
      setSubmitting(false);
    }
  }

  const label = target ? versionRoleLabel(target.role.memberType, target.role.vertical) : '';
  const lastRole = target ? isLastRole(target.member) : false;

  return (
    <Modal open={target !== null} onClose={onClose} title="Quitar rol de versión">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        {target && (
          <p className="text-sm text-ink-700">
            Se va a quitar el rol{' '}
            <span className="font-semibold text-ink-800">{label}</span> de{' '}
            <span className="font-semibold text-ink-800">{fullName(target.member)}</span> en la
            versión <span className="font-semibold text-ink-800">{versionName}</span>.
          </p>
        )}

        {target && lastRole && !administersAll && (
          <Alert tone="warning">
            Es su único rol en esta versión: va a volver a tener los permisos de su rol en el
            proyecto{fallbackRoleLabel ? ` (${fallbackRoleLabel})` : ''}, que pueden ser más
            amplios.
          </Alert>
        )}

        {target && losesOwnAdmin && (
          <Alert tone="warning">
            Estás quitando tu propio rol de Administrador de versión: podrías dejar de administrar
            esta versión.
          </Alert>
        )}

        <ModalFooter>
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="button" variant="danger" onClick={handleConfirm} loading={submitting}>
            Quitar rol
          </Button>
        </ModalFooter>
      </div>
    </Modal>
  );
}
