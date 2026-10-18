import { useId, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { SearchField } from '../components/ui/SearchField';
import { BranchIcon } from '../components/ui/icons';
import { useActiveProject } from '../hooks/useActiveProject';
import { formatDate } from '../utils/date';
import type { ProjectSummary } from '../types/project';

export function VersionsIndexPage() {
  const searchId = useId();
  const { projects, activeProject, isLoading, error, reload, selectProject } = useActiveProject();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');

  const ordered = useMemo(() => {
    if (!activeProject) {
      return projects;
    }
    const rest = projects.filter((project) => project.id !== activeProject.id);
    const current = projects.find((project) => project.id === activeProject.id);
    return current ? [current, ...rest] : projects;
  }, [projects, activeProject]);

  const visibleProjects = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) {
      return ordered;
    }
    return ordered.filter((project) => project.name.toLowerCase().includes(term));
  }, [ordered, search]);

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-800">Versionado</h1>
        <p className="mt-1 text-sm text-ink-500">
          Elegí un proyecto para ver y gestionar sus versiones.
        </p>
      </header>

      <Card padded={false} className="overflow-hidden">
        {projects.length > 1 && (
          <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 px-4 py-4">
            <SearchField
              id={searchId}
              label="Buscar proyectos"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder="Buscar proyectos"
              wrapperClassName="min-w-56 flex-1"
            />
          </div>
        )}

        {isLoading ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">Cargando proyectos…</p>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
            <div className="mt-4 text-center">
              <Button
                variant="secondary"
                onClick={() => {
                  void reload();
                }}
              >
                Reintentar
              </Button>
            </div>
          </div>
        ) : projects.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <p className="text-sm text-ink-500">
              Todavía no tenés proyectos. Creá uno para empezar a versionar.
            </p>
            <Button className="mt-4" onClick={() => navigate('/projects')}>
              Ir a Proyectos
            </Button>
          </div>
        ) : visibleProjects.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">
            Ningún proyecto coincide con la búsqueda.
          </p>
        ) : (
          <ul aria-label="Proyectos" className="divide-y divide-ink-100">
            {visibleProjects.map((project) => (
              <ProjectRow
                key={project.id}
                project={project}
                isLastUsed={project.id === activeProject?.id}
                onEnter={selectProject}
              />
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}

function ProjectRow({
  project,
  isLastUsed,
  onEnter,
}: {
  project: ProjectSummary;
  isLastUsed: boolean;
  onEnter: (projectId: string) => void;
}) {
  return (
    <li>
      <Link
        to={`/projects/${project.id}/versions`}
        onClick={() => onEnter(project.id)}
        className={
          'flex flex-wrap items-center gap-x-4 gap-y-2 px-4 py-3 transition-colors ' +
          'hover:bg-ink-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-inset ' +
          'focus-visible:ring-focus'
        }
      >
        <span className="min-w-0 flex-1">
          <span className="flex flex-wrap items-center gap-2">
            <span className="truncate font-semibold text-ink-800">{project.name}</span>
            {isLastUsed && (
              <span className="rounded-full border border-ink-200 px-2 py-0.5 text-[11px] font-medium text-ink-500">
                Último usado
              </span>
            )}
          </span>
          <span className="mt-0.5 block truncate text-sm text-ink-500">
            {project.organizationName}
          </span>
        </span>
        <span className="whitespace-nowrap text-sm text-ink-500">
          Actualizado el {formatDate(project.lastModified)}
        </span>
        <span
          className={
            'inline-flex shrink-0 items-center gap-1.5 rounded-lg border border-ink-100 ' +
            'px-2.5 py-1 text-sm font-semibold text-brand-800'
          }
        >
          <BranchIcon className="h-4 w-4" />
          Ver versiones
        </span>
      </Link>
    </li>
  );
}
