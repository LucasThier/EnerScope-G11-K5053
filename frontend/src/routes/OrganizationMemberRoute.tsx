import { Navigate, Outlet } from 'react-router-dom';
import { PageLoader } from '../components/ui/PageLoader';
import { useMyOrganizations } from '../hooks/useMyOrganizations';

export function OrganizationMemberRoute() {
  const { belongsToOrganization, loading, error } = useMyOrganizations();

  if (loading) {
    return <PageLoader />;
  }
  if (!belongsToOrganization && !error) {
    return <Navigate to="/app" replace />;
  }
  return <Outlet />;
}
