import { useState, type FormEvent } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { getErrorMessage } from '../../api/errors';
import { Button } from '../ui/Button';
import { TextField } from '../ui/TextField';
import { Alert } from '../ui/Alert';
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
  const [mail, setMail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const user = await login({ mail, password });
      onSuccess?.(user);
    } catch (err) {
      setError(getErrorMessage(err, 'Invalid email or password'));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} className={`flex flex-col gap-4 ${className}`} noValidate>
      {error && <Alert tone="error">{error}</Alert>}
      <TextField
        label="Email"
        type="email"
        autoComplete="email"
        required
        value={mail}
        onChange={(e) => setMail(e.target.value)}
        placeholder="you@enerscope.org"
      />
      <TextField
        label="Password"
        type="password"
        autoComplete="current-password"
        required
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="••••••••"
      />
      <Button type="submit" loading={submitting} className="mt-1 w-full">
        Sign in
      </Button>
    </form>
  );
}
