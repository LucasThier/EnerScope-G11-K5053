import { Navigate, Outlet, useParams } from 'react-router-dom';
import { PageLoader } from '../components/ui/PageLoader';
import { useOwnedOrganizations } from '../hooks/useOwnedOrganizations';

export function OrganizationOwnerRoute() {
  const { organizationId } = useParams();
  const { owned, loading } = useOwnedOrganizations();

  if (loading) {
    return <PageLoader />;
  }
  if (!owned.some((organization) => organization.id === organizationId)) {
    return <Navigate to="/app" replace />;
  }
  return <Outlet />;
}
