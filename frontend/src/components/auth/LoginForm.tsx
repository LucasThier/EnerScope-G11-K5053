import { useId, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { getErrorMessage } from '../../api/errors';
import { EyeIcon, EyeOffIcon } from '../ui/icons';
import type { UserSummary } from '../../types/auth';

interface LoginFormProps {
  /** Called with the authenticated user after a successful sign-in. */
  onSuccess?: (user: UserSummary) => void;
  className?: string;
}

/**
 * Self-contained sign-in form. Owns its own state and talks to {@link useAuth},
 * so it can be dropped into a page, a modal, or anywhere else unchanged.
 */
export function LoginForm({ onSuccess, className = '' }: LoginFormProps) {
  const { login } = useAuth();
  const id = useId();
  const mailRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement>(null);
  const pending = useRef(false);
  const [mail, setMail] = useState('');
  const [password, setPassword] = useState('');
  const [visible, setVisible] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [invalidField, setInvalidField] = useState<'mail' | 'password' | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending.current) return;
    setError(null);
    setInvalidField(null);
    if (!mail.trim() || !mailRef.current?.validity.valid) {
      setError('Ingresá un email válido.');
      setInvalidField('mail');
      mailRef.current?.focus();
      return;
    }
    if (!password) {
      setError('Ingresá tu contraseña.');
      setInvalidField('password');
      passwordRef.current?.focus();
      return;
    }
    pending.current = true;
    setSubmitting(true);
    try {
      const user = await login({ mail: mail.trim(), password });
      onSuccess?.(user);
    } catch (err) {
      setError(getErrorMessage(err, 'Email o contraseña incorrectos'));
    } finally {
      pending.current = false;
      setSubmitting(false);
    }
  }

  const inputClass = 'min-h-14 w-full rounded-xl border border-auth-control bg-auth-panel/95 px-4 py-3 text-base text-auth-panel-ink placeholder:text-auth-panel-muted transition-colors hover:border-auth-panel-muted focus-visible:border-auth-focus focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-auth-focus aria-invalid:border-auth-error read-only:opacity-70 motion-reduce:transition-none';

  return (
    <form onSubmit={handleSubmit} className={`flex flex-col ${className}`} noValidate aria-busy={submitting}>
      {error && <p id={`${id}-error`} role="alert" className="mb-6 rounded-xl border border-auth-error/40 bg-auth-error-surface px-4 py-3 text-sm leading-6 text-auth-error">{error}</p>}
      <label htmlFor={`${id}-mail`} className="mb-2 text-sm font-semibold text-auth-panel-ink">Email</label>
      <input
        ref={mailRef}
        id={`${id}-mail`}
        name="email"
        type="email"
        autoComplete="email"
        autoCapitalize="none"
        spellCheck={false}
        required
        readOnly={submitting}
        value={mail}
        onChange={(event) => setMail(event.target.value)}
        placeholder="nombre@empresa.com"
        aria-invalid={invalidField === 'mail' || undefined}
        aria-describedby={invalidField === 'mail' ? `${id}-error` : undefined}
        className={inputClass}
      />
      <label htmlFor={`${id}-password`} className="mt-[clamp(0.75rem,2vh,1.5rem)] mb-2 text-sm font-semibold text-auth-panel-ink">Contraseña</label>
      <div className="relative">
        <input
          ref={passwordRef}
          id={`${id}-password`}
          name="password"
          type={visible ? 'text' : 'password'}
          autoComplete="current-password"
          required
          readOnly={submitting}
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          placeholder="••••••••••••"
          aria-invalid={invalidField === 'password' || undefined}
          aria-describedby={invalidField === 'password' ? `${id}-error` : undefined}
          className={`${inputClass} pr-14`}
        />
        <button
          type="button"
          aria-label={visible ? 'Ocultar contraseña' : 'Mostrar contraseña'}
          aria-pressed={visible}
          onClick={() => setVisible((value) => !value)}
          className="absolute top-1 right-1 flex h-12 w-12 items-center justify-center rounded-lg text-auth-panel-muted transition-colors hover:bg-auth-page/10 hover:text-auth-panel-ink focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-auth-focus motion-reduce:transition-none"
        >
          {visible ? <EyeOffIcon className="h-5 w-5" /> : <EyeIcon className="h-5 w-5" />}
        </button>
      </div>
      <details className="mt-1 text-sm">
        <summary className="ml-auto flex min-h-11 w-fit cursor-pointer list-none items-center rounded-sm font-medium text-auth-panel-ink-strong underline decoration-auth-panel-ink-strong/40 underline-offset-4 hover:decoration-auth-panel-ink-strong focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-auth-focus [&::-webkit-details-marker]:hidden">¿Olvidaste tu contraseña?</summary>
        <p className="mt-2 rounded-xl border border-auth-divider bg-auth-panel/60 p-4 leading-6 text-auth-panel-muted sm:text-auth-panel-ink-strong">Contactá al administrador de tu organización para recibir ayuda con el acceso. La recuperación de contraseña por email todavía no está disponible.</p>
      </details>
      <button
        type="submit"
        disabled={submitting}
        className="group mt-[clamp(0.5rem,2vh,1.25rem)] inline-flex min-h-14 w-full items-center justify-center gap-4 rounded-xl bg-auth-action px-6 py-4 text-base font-semibold text-auth-action-text transition-colors hover:bg-auth-action-hover focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-auth-focus disabled:cursor-wait disabled:opacity-75 motion-reduce:transition-none"
      >
        <span>{submitting ? 'Ingresando…' : 'Ingresar'}</span>
        {submitting ? (
          <svg viewBox="0 0 24 24" fill="none" className="h-5 w-5 motion-safe:animate-spin" aria-hidden="true"><circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth="1.75" opacity="0.3" /><path d="M12 3a9 9 0 0 1 9 9" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" /></svg>
        ) : (
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" className="h-5 w-5 transition-transform group-hover:translate-x-1 motion-reduce:transform-none motion-reduce:transition-none" aria-hidden="true"><path d="M4 12h16m-6-6 6 6-6 6" /></svg>
        )}
      </button>
    </form>
  );
}
