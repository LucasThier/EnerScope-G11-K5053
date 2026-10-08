import { useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';

const PASSWORD_MIN = 8;

interface FieldErrors {
  currentPassword?: string;
  newPassword?: string;
  confirmation?: string;
}

export function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [changed, setChanged] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (!currentPassword) {
      errors.currentPassword = 'Ingresá tu contraseña actual.';
    }
    if (newPassword.length < PASSWORD_MIN) {
      errors.newPassword = `La contraseña nueva debe tener al menos ${PASSWORD_MIN} caracteres.`;
    }
    if (confirmation !== newPassword) {
      errors.confirmation = 'Las contraseñas no coinciden.';
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);
    setChanged(false);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      await usersApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setChanged(true);
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo cambiar la contraseña'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
      {submitError && <Alert tone="error">{submitError}</Alert>}
      {changed && <Alert tone="success">Se cambió la contraseña.</Alert>}

      <TextField
        label="Contraseña actual"
        type="password"
        autoComplete="current-password"
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
        error={fieldErrors.currentPassword}
      />

      <TextField
        label="Contraseña nueva"
        type="password"
        autoComplete="new-password"
        value={newPassword}
        onChange={(e) => setNewPassword(e.target.value)}
        error={fieldErrors.newPassword}
      />

      <TextField
        label="Repetir la contraseña nueva"
        type="password"
        autoComplete="new-password"
        value={confirmation}
        onChange={(e) => setConfirmation(e.target.value)}
        error={fieldErrors.confirmation}
      />

      <div className="mt-2 flex justify-end">
        <Button type="submit" loading={submitting}>
          Cambiar contraseña
        </Button>
      </div>
    </form>
  );
}
