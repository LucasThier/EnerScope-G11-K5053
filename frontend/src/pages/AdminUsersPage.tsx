import { Card } from '../components/ui/Card';
import { RegisterForm } from '../components/auth/RegisterForm';

/** Admin page: the single create-user form (platform user, or into an org). */
export function AdminUsersPage() {
  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-700">Create a user</h1>
        <p className="mt-1 text-sm text-ink-400">
          Creates a platform account. Optionally assign the new user to an organization —
          pick an existing one or create a new organization inline.
        </p>
      </header>
      <Card className="max-w-xl">
        <RegisterForm allowRoleSelection submitLabel="Create user" />
      </Card>
    </div>
  );
}
