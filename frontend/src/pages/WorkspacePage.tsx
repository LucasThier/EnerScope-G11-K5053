import type { ReactNode } from 'react';
import { Card } from '../components/ui/Card';
import { useAuth } from '../hooks/useAuth';

/** Landing page for regular platform users. */
export function WorkspacePage() {
  const { user } = useAuth();

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-800">
          {user?.firstName ? `Hola, ${user.firstName}` : 'Hola'}
        </h1>
        <p className="mt-1 text-sm text-ink-500">Este es tu espacio de trabajo en EnerScope.</p>
      </header>

      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Tu cuenta</h2>
        {/* Label and value are separated by size and weight, not by colour alone:
            the label is the small uppercase step of the type scale, the value is
            body text. Reading a field is then one glance rather than two. */}
        <dl className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Nombre">
            {user?.firstName} {user?.lastName}
          </Field>
          <Field label="Email">{user?.mail}</Field>
          <Field label="Rol">
            {user?.platformRole === 'ADMIN' ? 'Administrador' : 'Usuario'}
          </Field>
        </dl>
      </Card>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-1 text-sm text-ink-700">{children}</dd>
    </div>
  );
}
