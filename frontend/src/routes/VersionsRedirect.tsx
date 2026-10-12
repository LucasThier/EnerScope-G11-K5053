import { Link, Navigate } from 'react-router-dom';
import { PageLoader } from '../components/ui/PageLoader';
import { useActiveProject } from '../hooks/useActiveProject';

export function VersionsRedirect() {
  const { activeProject, isLoading } = useActiveProject();

  if (isLoading) {
    return <PageLoader />;
  }

  if (activeProject) {
    return <Navigate to={`/projects/${activeProject.id}/versions`} replace />;
  }

  return (
    <div>
      <h1 className="text-2xl font-semibold text-ink-800">Versionado</h1>
      <p className="mt-3 text-sm text-ink-500">
        Todavía no elegiste un proyecto. Elegí uno en{' '}
        <Link to="/projects" className="font-semibold text-brand-800 hover:underline">
          Proyectos
        </Link>{' '}
        para ver su versionado.
      </p>
    </div>
  );
}
