import { useState, type FormEvent } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { EyeIcon, EyeOffIcon } from '../ui/icons';
import { usersApi } from '../../api/users';
import { getErrorMessage } from '../../api/errors';

const PASSWORD_MIN = 8;

interface FieldErrors {
  currentPassword?: string;
  newPassword?: string;
  confirmation?: string;
}

interface Touched {
  currentPassword: boolean;
  newPassword: boolean;
  confirmation: boolean;
}

const UNTOUCHED: Touched = { currentPassword: false, newPassword: false, confirmation: false };

function ToggleVisibility({
  visible,
  onToggle,
  label,
}: {
  visible: boolean;
  onToggle: () => void;
  label: string;
}) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-pressed={visible}
      aria-label={visible ? `Ocultar ${label}` : `Mostrar ${label}`}
      className={
        'rounded-md p-1.5 text-ink-500 transition-colors hover:bg-ink-50 hover:text-ink-700 ' +
        'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
      }
    >
      {visible ? <EyeOffIcon className="h-4 w-4" /> : <EyeIcon className="h-4 w-4" />}
    </button>
  );
}

export function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [touched, setTouched] = useState<Touched>(UNTOUCHED);
  const [attempted, setAttempted] = useState(false);
  const [showCurrent, setShowCurrent] = useState(false);
  const [showNew, setShowNew] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);
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
    if (!confirmation || confirmation !== newPassword) {
      errors.confirmation = 'Las contraseñas no coinciden.';
    }
    return errors;
  }

  const errors = validate();
  const isValid = Object.keys(errors).length === 0;
  const visible = (field: keyof FieldErrors) => attempted || touched[field];

  function markTouched(field: keyof Touched) {
    setTouched((prev) => ({ ...prev, [field]: true }));
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSubmitError(null);
    setChanged(false);
    setAttempted(true);
    if (!isValid) {
      return;
    }

    setSubmitting(true);
    try {
      await usersApi.changePassword({ currentPassword, newPassword });
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
      setTouched(UNTOUCHED);
      setAttempted(false);
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
        type={showCurrent ? 'text' : 'password'}
        autoComplete="current-password"
        value={currentPassword}
        onChange={(e) => setCurrentPassword(e.target.value)}
        onBlur={() => markTouched('currentPassword')}
        error={visible('currentPassword') ? errors.currentPassword : undefined}
        endAdornment={
          <ToggleVisibility
            visible={showCurrent}
            onToggle={() => setShowCurrent((v) => !v)}
            label="la contraseña actual"
          />
        }
      />

      <div className="flex flex-col gap-1">
        <TextField
          label="Contraseña nueva"
          type={showNew ? 'text' : 'password'}
          autoComplete="new-password"
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
          onBlur={() => markTouched('newPassword')}
          error={visible('newPassword') ? errors.newPassword : undefined}
          endAdornment={
            <ToggleVisibility
              visible={showNew}
              onToggle={() => setShowNew((v) => !v)}
              label="la contraseña nueva"
            />
          }
        />
        <p className="text-xs text-ink-500">
          Debe tener al menos {PASSWORD_MIN} caracteres.
        </p>
      </div>

      <TextField
        label="Repetir la contraseña nueva"
        type={showConfirm ? 'text' : 'password'}
        autoComplete="new-password"
        value={confirmation}
        onChange={(e) => setConfirmation(e.target.value)}
        onBlur={() => markTouched('confirmation')}
        error={visible('confirmation') ? errors.confirmation : undefined}
        endAdornment={
          <ToggleVisibility
            visible={showConfirm}
            onToggle={() => setShowConfirm((v) => !v)}
            label="la repetición de la contraseña"
          />
        }
      />

      <div className="mt-2 flex justify-end">
        <Button type="submit" loading={submitting} disabled={!isValid}>
          Cambiar contraseña
        </Button>
      </div>
    </form>
  );
}
