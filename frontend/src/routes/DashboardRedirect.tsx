import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

/** Sends every signed-in user to the dashboard, regardless of platform role. */
export function DashboardRedirect() {
  const { user } = useAuth();
  if (!user) {
    return <Navigate to="/login" replace />;
  }
  return <Navigate to="/app" replace />;
}
