import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { PageLoader } from '../components/ui/PageLoader';

/** Gate for authenticated routes. Waits for the session bootstrap, then either
 * renders the nested routes or bounces to the login page. */
export function ProtectedRoute() {
  const { isAuthenticated, isLoading } = useAuth();
  if (isLoading) {
    return <PageLoader />;
  }
  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }
  return <Outlet />;
}
