import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import type { PlatformRole } from '../types/auth';

interface RoleRouteProps {
  role: PlatformRole;
}

/** Gate for role-restricted routes. Assumes it is nested inside ProtectedRoute
 * (so the user is already authenticated); redirects home on a role mismatch. */
export function RoleRoute({ role }: RoleRouteProps) {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  if (user.platformRole !== role) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
