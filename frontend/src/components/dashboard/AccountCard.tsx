import { Link } from 'react-router-dom';
import { Card } from '../ui/Card';
import { useAuth } from '../../hooks/useAuth';

export function AccountCard() {
  const { user } = useAuth();

  return (
    <Card>
      <div className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold text-ink-800">Cuenta</h2>
          <p className="mt-1 text-sm text-ink-700">
            {user?.firstName} {user?.lastName}
          </p>
          <p className="text-sm text-ink-500">{user?.mail}</p>
        </div>
        <span className="rounded-full bg-ink-50 px-2 py-1 text-xs font-medium text-ink-600">
          {user?.platformRole === 'ADMIN' ? 'Administrador' : 'Usuario'}
        </span>
      </div>
      <Link
        to="/profile"
        className="mt-4 inline-block text-sm font-semibold text-brand-800 hover:underline"
      >
        Ver perfil
      </Link>
    </Card>
  );
}
