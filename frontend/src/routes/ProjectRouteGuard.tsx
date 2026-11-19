import { Navigate, Outlet, useMatch } from 'react-router-dom';
import { PageLoader } from '../components/ui/PageLoader';
import { PROJECT_ROUTE_PATTERN, useActiveProject } from '../hooks/useActiveProject';
import { projectRouteRedirect } from '../utils/activeProject';

export function ProjectRouteGuard() {
  const match = useMatch(PROJECT_ROUTE_PATTERN);
  const { projects, storedProjectId, isLoading, error } = useActiveProject();
  if (!match || error) {
    return <Outlet />;
  }
  if (isLoading && projects.length === 0) {
    return <PageLoader />;
  }
  const target = projectRouteRedirect(
    projects,
    match.params.projectId ?? '',
    storedProjectId,
    match.params['*'] ?? '',
  );
  return target ? <Navigate to={target} replace /> : <Outlet />;
}
