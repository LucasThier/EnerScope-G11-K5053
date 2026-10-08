import { useEffect, useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../hooks/useAuth';
import type { UserListItem } from '../../types/auth';

interface DeactivateUserDialogProps {
  user: UserListItem | null;
  onClose: () => void;
  onDeactivated: () => Promise<void>;
}

export function DeactivateUserDialog({
  user,
  onClose,
  onDeactivated,
}: DeactivateUserDialogProps) {
  const { user: caller } = useAuth();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      setError(null);
    }
  }, [user]);

  const isSelf = user !== null && caller !== null && user.id === caller.id;

  async function handleConfirm() {
    if (!user) {
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await usersApi.deactivate(user.id);
      await onDeactivated();
      onClose();
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudo dar de baja la cuenta'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal open={user !== null} onClose={onClose} title="Dar de baja la cuenta">
      <div className="flex flex-col gap-4">
        {error && <Alert tone="error">{error}</Alert>}

        <p className="text-sm text-ink-700">
          Se va a dar de baja a{' '}
          <span className="font-semibold text-ink-800">
            {user?.firstName} {user?.lastName}
          </span>
          . La cuenta deja de poder iniciar sesión.
        </p>
        <p className="text-sm text-ink-500">
          Sus organizaciones y proyectos se conservan, donde va a figurar como inactiva. Un
          administrador de plataforma puede reactivarla desde esta misma pantalla.
        </p>

        {isSelf && (
          <Alert tone="error">
            Estás dando de baja tu propia cuenta. Vas a perder el acceso a la aplicación en
            cuanto expire tu sesión actual, y vas a necesitar que otro administrador te
            reactive.
          </Alert>
        )}

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
