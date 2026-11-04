import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ArrowRightIcon, ChevronRightIcon, FolderIcon } from '../ui/icons';
import { useActiveProject } from '../../hooks/useActiveProject';
import { formatDateShort } from '../../utils/date';
import { hasReliableRecency, sortByRecency } from '../../utils/recentProjects';

const RECENT_COUNT = 5;

export function RecentProjectsList() {
  const { projects, activeProject, isLoading, error, selectProject, reload } = useActiveProject();

  const reliable = hasReliableRecency(projects);
  const visible = useMemo(
    () => (reliable ? sortByRecency(projects) : projects).slice(0, RECENT_COUNT),
    [projects, reliable],
  );

  return (
    <Card padded={false} className="min-w-0">
      <div className="flex items-center justify-between gap-4 px-6 py-5">
        <h2 className="text-lg font-semibold text-ink-800">
          {reliable || isLoading ? 'Proyectos recientes' : 'Proyectos'}
        </h2>
        <Link
          to="/projects"
          className={
            'inline-flex shrink-0 items-center gap-1 rounded text-sm font-medium text-brand-800 ' +
            'underline underline-offset-2 hover:text-brand-900 focus:outline-none ' +
            'focus-visible:ring-2 focus-visible:ring-focus'
          }
        >
          Ver todos
          <ArrowRightIcon className="h-3.5 w-3.5" />
        </Link>
      </div>

      {isLoading ? (
        <ul role="status" className="px-3 pb-3">
          <span className="sr-only">Cargando proyectos</span>
          {Array.from({ length: 3 }, (_, index) => (
            <li key={index} className="flex items-center gap-3 px-3 py-3">
              <div className="h-10 w-10 rounded-control bg-ink-100 motion-safe:animate-pulse" />
              <div className="flex-1 space-y-2">
                <div className="h-4 w-1/2 rounded bg-ink-100 motion-safe:animate-pulse" />
                <div className="h-3 w-1/3 rounded bg-ink-100 motion-safe:animate-pulse" />
              </div>
            </li>
          ))}
        </ul>
      ) : error ? (
        <div className="px-6 pb-6">
          <Alert tone="error">{error}</Alert>
          <Button variant="secondary" size="compact" className="mt-4" onClick={() => void reload()}>
            Reintentar
          </Button>
        </div>
      ) : visible.length === 0 ? (
        <p className="px-6 pb-8 text-sm text-ink-500">Todavía no hay proyectos.</p>
      ) : (
        <ul className="px-3 pb-3">
          {visible.map((project) => {
            const isSelected = activeProject?.id === project.id;
            const date = formatDateShort(project.lastModified);
            return (
              <li key={project.id}>
                <Link
                  to={`/projects/${project.id}/versions`}
                  onClick={() => selectProject(project.id)}
                  aria-current={isSelected ? 'true' : undefined}
                  className={
                    'flex min-h-control items-center gap-3 rounded-control border-l-[3px] px-3 py-2 ' +
                    'transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
                    'motion-reduce:transition-none ' +
                    (isSelected
                      ? 'border-brand-800 bg-brand-50'
                      : 'border-transparent hover:bg-ink-50')
                  }
                >
                  <FolderIcon className="h-6 w-6 shrink-0 text-ink-500" />
                  <span className="min-w-0 flex-1">
                    <span
                      className="block truncate text-sm font-semibold text-ink-800"
                      title={project.name}
                    >
                      {project.name}
                    </span>
                    <span className="block truncate text-xs text-ink-500">
                      {project.organizationName || 'Sin organización'}
                      {date && <span className="sm:hidden"> · {date}</span>}
                    </span>
                  </span>
                  {date && (
                    <span className="hidden shrink-0 text-xs text-ink-500 sm:block">{date}</span>
                  )}
                  <ChevronRightIcon className="h-4 w-4 shrink-0 text-ink-400" />
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
