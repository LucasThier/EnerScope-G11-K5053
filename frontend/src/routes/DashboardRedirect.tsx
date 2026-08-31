import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/** Sends each user to the panel that matches their platform role. */
export function DashboardRedirect() {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to={user.platformRole === 'ADMIN' ? '/admin/users' : '/app'} replace />;
}
