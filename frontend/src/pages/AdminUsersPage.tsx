import { Card } from '../components/ui/Card';
import { RegisterForm } from '../components/auth/RegisterForm';

/** Admin page: the single create-user form (platform user, or into an org). */
export function AdminUsersPage() {
  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-800">Crear usuario</h1>
        <p className="mt-1 text-sm text-ink-500">
          Crea una cuenta de plataforma. Opcionalmente podés asignar al usuario a una
          organización: elegí una existente o creá una nueva ahí mismo.
        </p>
      </header>
      <Card className="max-w-xl">
        <RegisterForm allowRoleSelection submitLabel="Crear usuario" />
      </Card>
    </div>
  );
}
