import { useEffect, useState, type FormEvent } from 'react';
import { Alert } from '../components/ui/Alert';
import { ChangePasswordForm } from '../components/auth/ChangePasswordForm';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { TextField } from '../components/ui/TextField';
import { usersApi } from '../api/users';
import { getErrorMessage } from '../api/errors';
import { useAuth } from '../hooks/useAuth';
import type { UpdateProfileRequest } from '../types/auth';

const NAME_MIN = 2;
const NAME_MAX = 60;
const JOB_TITLE_MAX = 120;

interface FieldErrors {
  firstName?: string;
  lastName?: string;
  jobTitle?: string;
}

export function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName);
      setLastName(user.lastName);
      setJobTitle(user.jobTitle ?? '');
    }
  }, [user]);

  if (!user) {
    return null;
  }

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    const trimmedFirst = firstName.trim();
    const trimmedLast = lastName.trim();
    if (trimmedFirst.length < NAME_MIN || trimmedFirst.length > NAME_MAX) {
      errors.firstName = `El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    }
    if (trimmedLast.length < NAME_MIN || trimmedLast.length > NAME_MAX) {
      errors.lastName = `El apellido debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    }
    if (jobTitle.trim().length > JOB_TITLE_MAX) {
      errors.jobTitle = `El puesto no puede superar los ${JOB_TITLE_MAX} caracteres.`;
    }
    return errors;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) {
      return;
    }
    setSubmitError(null);
    setSaved(false);
    const errors = validate();
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    const trimmedFirst = firstName.trim();
    const trimmedLast = lastName.trim();
    const trimmedJobTitle = jobTitle.trim();
    const changes: UpdateProfileRequest = {};
    if (trimmedFirst !== user.firstName) {
      changes.firstName = trimmedFirst;
    }
    if (trimmedLast !== user.lastName) {
      changes.lastName = trimmedLast;
    }
    if (trimmedJobTitle !== (user.jobTitle ?? '')) {
      changes.jobTitle = trimmedJobTitle;
    }
    if (Object.keys(changes).length === 0) {
      setSaved(true);
      return;
    }

    setSubmitting(true);
    try {
      const res = await usersApi.updateProfile(changes);
      const updated = res.data.data;
      if (!updated) {
        throw new Error(res.data.message || 'No se pudo guardar el perfil');
      }
      updateUser(updated);
      setSaved(true);
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo guardar el perfil'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-800">Mi perfil</h1>
        <p className="mt-1 text-sm text-ink-500">
          Tu nombre y tu puesto, como los ve el resto del equipo en los proyectos y las
          organizaciones.
        </p>
      </header>

      <Card className="max-w-xl">
        <h2 className="text-lg font-semibold text-ink-800">Datos personales</h2>
        <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4" noValidate>
          {submitError && <Alert tone="error">{submitError}</Alert>}
          {saved && <Alert tone="success">Se guardaron los cambios.</Alert>}

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <TextField
              label="Nombre"
              value={firstName}
              onChange={(e) => setFirstName(e.target.value)}
              error={fieldErrors.firstName}
              maxLength={NAME_MAX}
            />
            <TextField
              label="Apellido"
              value={lastName}
              onChange={(e) => setLastName(e.target.value)}
              error={fieldErrors.lastName}
              maxLength={NAME_MAX}
            />
          </div>

          <TextField
            label="Puesto"
            value={jobTitle}
            onChange={(e) => setJobTitle(e.target.value)}
            error={fieldErrors.jobTitle}
            placeholder="Analista de inversiones"
            maxLength={JOB_TITLE_MAX}
          />

          <div className="flex flex-col gap-1">
            <span className="text-sm font-medium text-ink-600">Email</span>
            <span className="text-sm text-ink-500">{user.mail}</span>
          </div>

          <div className="mt-2 flex justify-end">
            <Button type="submit" loading={submitting}>
              Guardar cambios
            </Button>
          </div>
        </form>
      </Card>

      <Card className="mt-6 max-w-xl">
        <h2 className="text-lg font-semibold text-ink-800">Contraseña</h2>
        <p className="mt-1 text-sm text-ink-500">
          Si tu cuenta la creó un administrador, acá podés reemplazar la contraseña que te
          entregaron.
        </p>
        <div className="mt-4">
          <ChangePasswordForm />
        </div>
      </Card>
    </div>
  );
}
