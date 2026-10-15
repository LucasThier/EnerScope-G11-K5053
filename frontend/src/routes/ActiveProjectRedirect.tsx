import { Navigate } from 'react-router-dom';
import { Spinner } from '../components/ui/Spinner';
import { useActiveProject } from '../hooks/useActiveProject';

interface ActiveProjectRedirectProps {
  /** The project-scoped section to open, e.g. `editor` → `/projects/:id/editor`. */
  section: 'editor' | 'scenarios' | 'compare';
}

/**
 * Entry points that are not tied to a project in the URL (`/editor`,
 * `/scenarios`, `/compare`) open the active project's page. With no project at
 * all the user lands on the projects list, where they can create one.
 */
export function ActiveProjectRedirect({ section }: ActiveProjectRedirectProps) {
  const { activeProject, isLoading } = useActiveProject();
  if (isLoading) {
    return (
      <div className="flex flex-1 items-center justify-center p-8 text-brand-800">
        <Spinner className="h-6 w-6" />
      </div>
    );
  }
  if (!activeProject) {
    return <Navigate to="/projects" replace />;
  }
  return <Navigate to={`/projects/${activeProject.id}/${section}`} replace />;
}
