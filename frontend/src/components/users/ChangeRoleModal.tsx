import { useEffect, useId, useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';
import type { PlatformRole, UserListItem } from '../../types/auth';

interface ChangeRoleModalProps {
  user: UserListItem | null;
  onClose: () => void;
  onChanged: () => Promise<void>;
}

const selectClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

export function ChangeRoleModal({ user, onClose, onChanged }: ChangeRoleModalProps) {
  const selectId = useId();
  const [role, setRole] = useState<PlatformRole>('USER');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      setRole(user.platformRole);
      setError(null);
    }
  }, [user]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) {
      return;
    }
    if (role === user.platformRole) {
      onClose();
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      await usersApi.updateRole(user.id, { platformRole: role });
      await onChanged();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo cambiar el rol'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={user !== null} onClose={onClose} title="Cambiar rol de plataforma">
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {error && <Alert tone="error">{error}</Alert>}

        <p className="text-sm text-ink-700">
          <span className="font-semibold text-ink-800">
            {user?.firstName} {user?.lastName}
          </span>{' '}
          <span className="text-ink-500">({user?.mail})</span>
        </p>

        <div className="flex flex-col gap-2">
          <label htmlFor={selectId} className="text-sm font-medium text-ink-600">
            Rol de plataforma
          </label>
          <select
            id={selectId}
            value={role}
            onChange={(e) => setRole(e.target.value as PlatformRole)}
            className={selectClasses}
          >
            <option value="USER">Usuario</option>
            <option value="ADMIN">Administrador</option>
          </select>
        </div>

        <p className="text-sm text-ink-500">
          Un administrador de plataforma accede a todas las organizaciones y a todos los
          proyectos.
        </p>

        <div className="mt-2 flex justify-end gap-2">
          <Button type="button" variant="ghost" onClick={onClose} disabled={submitting}>
            Cancelar
          </Button>
          <Button type="submit" loading={submitting}>
            Guardar rol
          </Button>
        </div>
      </form>
    </Modal>
  );
}
