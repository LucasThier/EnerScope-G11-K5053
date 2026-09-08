import { Card } from '../components/ui/Card';
import { useAuth } from '../hooks/useAuth';

/** Landing page for regular platform users. */
export function WorkspacePage() {
  const { user } = useAuth();

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-700">
          Welcome, {user?.firstName ?? 'there'}
        </h1>
        <p className="mt-1 text-sm text-ink-400">This is your EnerScope workspace.</p>
      </header>

      <Card>
        <h2 className="text-lg font-semibold text-ink-700">Your account</h2>
        <dl className="mt-4 grid grid-cols-1 gap-3 text-sm sm:grid-cols-2">
          <div>
            <dt className="text-ink-400">Name</dt>
            <dd className="text-ink-700">
              {user?.firstName} {user?.lastName}
            </dd>
          </div>
          <div>
            <dt className="text-ink-400">Email</dt>
            <dd className="text-ink-700">{user?.mail}</dd>
          </div>
          <div>
            <dt className="text-ink-400">Role</dt>
            <dd className="text-ink-700">{user?.platformRole}</dd>
          </div>
        </dl>
      </Card>
    </div>
  );
}
