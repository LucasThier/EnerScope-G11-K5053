import { useEffect, useRef, useState, type FormEvent, type MouseEvent } from 'react';
import { useLocation } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Avatar } from '../components/ui/Avatar';
import { ChangePasswordForm } from '../components/auth/ChangePasswordForm';
import { AppearanceSettings } from '../components/settings/AppearanceSettings';
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

type Section = 'perfil' | 'seguridad' | 'configuracion';

const SECTIONS: { id: Section; label: string }[] = [
  { id: 'perfil', label: 'Perfil' },
  { id: 'seguridad', label: 'Seguridad' },
  { id: 'configuracion', label: 'Configuración' },
];
const LAST_SECTION = SECTIONS[SECTIONS.length - 1].id;

interface FieldErrors {
  firstName?: string;
  lastName?: string;
  jobTitle?: string;
}

interface Touched {
  firstName: boolean;
  lastName: boolean;
  jobTitle: boolean;
}

const UNTOUCHED: Touched = { firstName: false, lastName: false, jobTitle: false };

function navLinkClasses(isActive: boolean): string {
  return (
    'shrink-0 rounded-lg px-3 py-2 text-sm transition-colors focus:outline-none ' +
    'focus-visible:ring-2 focus-visible:ring-focus ' +
    (isActive ? 'bg-brand-50 font-semibold text-brand-800' : 'font-medium text-ink-500 hover:bg-ink-50 hover:text-ink-700')
  );
}

export function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [firstName, setFirstName] = useState('');
  const [lastName, setLastName] = useState('');
  const [jobTitle, setJobTitle] = useState('');
  const [touched, setTouched] = useState<Touched>(UNTOUCHED);
  const [attempted, setAttempted] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [active, setActive] = useState<Section>('perfil');
  const location = useLocation();
  const pageRef = useRef<HTMLDivElement>(null);

  const perfilRef = useRef<HTMLDivElement>(null);
  const seguridadRef = useRef<HTMLDivElement>(null);
  const configuracionRef = useRef<HTMLDivElement>(null);

  function sectionRef(id: Section) {
    if (id === 'perfil') return perfilRef;
    if (id === 'seguridad') return seguridadRef;
    return configuracionRef;
  }

  useEffect(() => {
    if (user) {
      setFirstName(user.firstName);
      setLastName(user.lastName);
      setJobTitle(user.jobTitle ?? '');
    }
  }, [user]);

  useEffect(() => {
    const entries: { id: Section; el: HTMLDivElement }[] = [];
    if (perfilRef.current) {
      entries.push({ id: 'perfil', el: perfilRef.current });
    }
    if (seguridadRef.current) {
      entries.push({ id: 'seguridad', el: seguridadRef.current });
    }
    if (configuracionRef.current) {
      entries.push({ id: 'configuracion', el: configuracionRef.current });
    }
    if (entries.length === 0) {
      return;
    }

    const scrollParent = pageRef.current?.closest('main') ?? null;

    function isAtBottom() {
      if (!scrollParent) {
        return false;
      }
      return (
        scrollParent.scrollTop + scrollParent.clientHeight >= scrollParent.scrollHeight - 2
      );
    }

    function handleScroll() {
      if (isAtBottom()) {
        setActive(LAST_SECTION);
      }
    }

    const observer = new IntersectionObserver(
      (observed) => {
        if (isAtBottom()) {
          setActive(LAST_SECTION);
          return;
        }
        const visible = observed
          .filter((entry) => entry.isIntersecting)
          .sort((a, b) => a.boundingClientRect.top - b.boundingClientRect.top);
        if (visible.length === 0) {
          return;
        }
        const match = entries.find((entry) => entry.el === visible[0].target);
        if (match) {
          setActive(match.id);
        }
      },
      { rootMargin: '-96px 0px -60% 0px', threshold: 0 },
    );
    entries.forEach(({ el }) => observer.observe(el));
    scrollParent?.addEventListener('scroll', handleScroll, { passive: true });

    const hashId = location.hash.replace('#', '') as Section;
    const target = entries.find((entry) => entry.id === hashId);
    if (target) {
      target.el.scrollIntoView({ behavior: 'auto', block: 'start' });
      setActive(target.id);
    }

    return () => {
      observer.disconnect();
      scrollParent?.removeEventListener('scroll', handleScroll);
    };
  }, [location.hash]);

  if (!user) {
    return null;
  }

  function goToSection(id: Section, event: MouseEvent<HTMLAnchorElement>) {
    event.preventDefault();
    const reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    sectionRef(id).current?.scrollIntoView({ behavior: reduceMotion ? 'auto' : 'smooth', block: 'start' });
    window.history.pushState(null, '', `#${id}`);
    setActive(id);
  }

  function markTouched(field: keyof Touched) {
    setTouched((prev) => ({ ...prev, [field]: true }));
  }

  const trimmedFirst = firstName.trim();
  const trimmedLast = lastName.trim();
  const trimmedJobTitle = jobTitle.trim();

  function validate(): FieldErrors {
    const errors: FieldErrors = {};
    if (trimmedFirst.length < NAME_MIN || trimmedFirst.length > NAME_MAX) {
      errors.firstName = `El nombre debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    }
    if (trimmedLast.length < NAME_MIN || trimmedLast.length > NAME_MAX) {
      errors.lastName = `El apellido debe tener entre ${NAME_MIN} y ${NAME_MAX} caracteres.`;
    }
    if (trimmedJobTitle.length > JOB_TITLE_MAX) {
      errors.jobTitle = `El puesto no puede superar los ${JOB_TITLE_MAX} caracteres.`;
    }
    return errors;
  }

  const errors = validate();
  const isValid = Object.keys(errors).length === 0;
  const hasChanges =
    trimmedFirst !== user.firstName ||
    trimmedLast !== user.lastName ||
    trimmedJobTitle !== (user.jobTitle ?? '');
  const visible = (field: keyof FieldErrors) => attempted || touched[field];

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) {
      return;
    }
    setSubmitError(null);
    setSaved(false);
    setAttempted(true);
    if (!isValid || !hasChanges) {
      return;
    }

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

    setSubmitting(true);
    try {
      const res = await usersApi.updateProfile(changes);
      const updated = res.data.data;
      if (!updated) {
        throw new Error(res.data.message || 'No se pudo guardar el perfil');
      }
      updateUser(updated);
      setTouched(UNTOUCHED);
      setAttempted(false);
      setSaved(true);
    } catch (err) {
      setSubmitError(getErrorMessage(err, 'No se pudo guardar el perfil'));
    } finally {
      setSubmitting(false);
    }
  }

  const subtitle = user.jobTitle ?? (user.platformRole === 'ADMIN' ? 'Administrador' : 'Usuario');

  return (
    <div ref={pageRef}>
      <header className="mb-8">
        <h1 className="text-2xl font-semibold text-ink-800">Mi perfil</h1>
        <div className="mt-4 flex items-center gap-3">
          <Avatar firstName={user.firstName} lastName={user.lastName} />
          <div className="min-w-0">
            <p className="truncate text-base font-semibold text-ink-800">
              {user.firstName} {user.lastName}
            </p>
            <p className="truncate text-sm text-ink-500">
              {subtitle} · {user.mail}
            </p>
          </div>
        </div>
      </header>

      <div className="flex flex-col gap-6 lg:flex-row lg:items-start lg:gap-10">
        <nav
          aria-label="Secciones del perfil"
          className="flex gap-2 overflow-x-auto lg:sticky lg:top-8 lg:w-44 lg:shrink-0 lg:flex-col lg:gap-1 lg:overflow-visible"
        >
          {SECTIONS.map((section) => (
            <a
              key={section.id}
              href={`#${section.id}`}
              aria-current={active === section.id ? 'true' : undefined}
              onClick={(event) => goToSection(section.id, event)}
              className={navLinkClasses(active === section.id)}
            >
              {section.label}
            </a>
          ))}
        </nav>

        <div className="flex min-w-0 max-w-2xl flex-1 flex-col gap-8">
          <section id="perfil" ref={perfilRef} aria-labelledby="perfil-heading">
            <Card>
              <h2 id="perfil-heading" className="text-lg font-semibold text-ink-800">
                Datos personales
              </h2>
              <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4" noValidate>
                {submitError && <Alert tone="error">{submitError}</Alert>}
                {saved && <Alert tone="success">Se guardaron los cambios.</Alert>}

                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                  <TextField
                    label="Nombre"
                    value={firstName}
                    onChange={(e) => setFirstName(e.target.value)}
                    onBlur={() => markTouched('firstName')}
                    error={visible('firstName') ? errors.firstName : undefined}
                    maxLength={NAME_MAX}
                  />
                  <TextField
                    label="Apellido"
                    value={lastName}
                    onChange={(e) => setLastName(e.target.value)}
                    onBlur={() => markTouched('lastName')}
                    error={visible('lastName') ? errors.lastName : undefined}
                    maxLength={NAME_MAX}
                  />
                </div>

                <TextField
                  label="Puesto"
                  value={jobTitle}
                  onChange={(e) => setJobTitle(e.target.value)}
                  onBlur={() => markTouched('jobTitle')}
                  error={visible('jobTitle') ? errors.jobTitle : undefined}
                  placeholder="Analista de inversiones"
                  maxLength={JOB_TITLE_MAX}
                />

                <div className="flex flex-col gap-1">
                  <span className="text-sm font-medium text-ink-600">Email</span>
                  <span className="text-sm text-ink-500">{user.mail}</span>
                  <span className="text-xs text-ink-500">No se puede modificar desde acá.</span>
                </div>

                <div className="mt-2 flex justify-end">
                  <Button type="submit" loading={submitting} disabled={!hasChanges || !isValid}>
                    Guardar cambios
                  </Button>
                </div>
              </form>
            </Card>
          </section>

          <section id="seguridad" ref={seguridadRef} aria-labelledby="seguridad-heading">
            <Card>
              <h2 id="seguridad-heading" className="text-lg font-semibold text-ink-800">
                Seguridad
              </h2>
              <div className="mt-4">
                <h3 className="text-sm font-semibold text-ink-800">Cambiar contraseña</h3>
                <p className="mt-1 text-sm text-ink-500">
                  Si tu cuenta la creó un administrador, acá podés reemplazar la contraseña que te
                  entregaron.
                </p>
                <div className="mt-4">
                  <ChangePasswordForm />
                </div>
              </div>
            </Card>
          </section>

          <section id="configuracion" ref={configuracionRef} aria-labelledby="configuracion-heading">
            <Card>
              <h2 id="configuracion-heading" className="text-lg font-semibold text-ink-800">
                Configuración
              </h2>
              <div className="mt-4">
                <h3 className="text-sm font-semibold text-ink-800">Apariencia</h3>
                <AppearanceSettings />
              </div>
            </Card>
          </section>
        </div>
      </div>
    </div>
  );
}
