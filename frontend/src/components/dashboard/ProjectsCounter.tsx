import { Link } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { ArrowRightIcon, FolderIcon } from '../ui/icons';
import { useActiveProject } from '../../hooks/useActiveProject';

/** The one indicator Inicio keeps: how many projects the person can open, and the way to the full list. */
export function ProjectsCounter() {
  const { projects, isLoading, error } = useActiveProject();

  return (
    <Card padded={false} className="flex h-full items-center gap-4 p-5 [@media(max-height:760px)]:p-4">
      <span
        className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-800"
        aria-hidden="true"
      >
        <FolderIcon className="h-6 w-6" />
      </span>
      <div className="min-w-0">
        <p className="text-sm text-ink-700">Proyectos</p>
        {isLoading ? (
          <div role="status" className="my-1 h-8 w-12 rounded-control bg-ink-100 motion-safe:animate-pulse">
            <span className="sr-only">Cargando proyectos</span>
          </div>
        ) : error ? (
          <Alert tone="error" className="mt-1">
            {error}
          </Alert>
        ) : (
          <p className="text-3xl font-bold leading-tight text-ink-800">{projects.length}</p>
        )}
        <Link
          to="/projects"
          className={
            'inline-flex items-center gap-1 rounded text-sm font-medium text-brand-800 underline ' +
            'underline-offset-2 hover:text-brand-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
          }
        >
          Ver todos los proyectos
          <ArrowRightIcon className="h-3.5 w-3.5" />
        </Link>
      </div>
    </Card>
  );
}
