import { useId, useMemo, useState } from 'react';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { PlusIcon, SearchIcon } from '../components/ui/icons';
import { NewProjectModal } from '../components/projects/NewProjectModal';
import { ProjectsTable } from '../components/projects/ProjectsTable';
import { useActiveProject } from '../hooks/useActiveProject';

const ALL_ORGANIZATIONS = 'all';

const controlClasses =
  'rounded-lg border border-ink-200 bg-white px-3 py-2 text-sm text-ink-800 ' +
  'focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-400/40';

export function ProjectsPage() {
  const searchId = useId();
  const organizationFilterId = useId();
  const { projects, isLoading, error, reload } = useActiveProject();
  const [search, setSearch] = useState('');
  const [organizationId, setOrganizationId] = useState(ALL_ORGANIZATIONS);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const organizations = useMemo(() => {
    const byId = new Map<string, string>();
    for (const project of projects) {
      byId.set(project.organizationId, project.organizationName);
    }
    return [...byId.entries()]
      .map(([id, name]) => ({ id, name }))
      .sort((a, b) => a.name.localeCompare(b.name, 'es'));
  }, [projects]);

  const visibleProjects = useMemo(() => {
    const term = search.trim().toLowerCase();
    return projects.filter((project) => {
      const matchesOrganization =
        organizationId === ALL_ORGANIZATIONS || project.organizationId === organizationId;
      if (!matchesOrganization) {
        return false;
      }
      if (!term) {
        return true;
      }
      return (
        project.name.toLowerCase().includes(term) ||
        project.description.toLowerCase().includes(term) ||
        project.organizationName.toLowerCase().includes(term)
      );
    });
  }, [projects, search, organizationId]);

  return (
    <div>
      <header className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold text-ink-800">Proyectos</h1>
          <p className="mt-1 text-sm text-ink-500">
            Los proyectos de los que formás parte, con su organización y su equipo.
          </p>
        </div>
        <Button onClick={() => setIsModalOpen(true)}>
          <PlusIcon className="h-4 w-4" />
          Nuevo proyecto
        </Button>
      </header>

      <Card padded={false} className="overflow-hidden">
        <div className="flex flex-wrap items-center gap-3 border-b border-ink-100 px-4 py-4">
          <div className="relative min-w-56 flex-1">
            <SearchIcon className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-ink-400" />
            <label htmlFor={searchId} className="sr-only">
              Buscar proyectos
            </label>
            <input
              id={searchId}
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Buscar proyectos"
              className={`w-full pl-9 placeholder:text-ink-400 ${controlClasses}`}
            />
          </div>
          <label htmlFor={organizationFilterId} className="sr-only">
            Filtrar por organización
          </label>
          <select
            id={organizationFilterId}
            value={organizationId}
            onChange={(e) => setOrganizationId(e.target.value)}
            className={controlClasses}
          >
            <option value={ALL_ORGANIZATIONS}>Todas las organizaciones</option>
            {organizations.map((organization) => (
              <option key={organization.id} value={organization.id}>
                {organization.name}
              </option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">Cargando proyectos…</p>
        ) : error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : projects.length === 0 ? (
          <div className="px-4 py-8 text-center">
            <p className="text-sm text-ink-500">Todavía no hay proyectos.</p>
            <Button className="mt-4" onClick={() => setIsModalOpen(true)}>
              <PlusIcon className="h-4 w-4" />
              Nuevo proyecto
            </Button>
          </div>
        ) : visibleProjects.length === 0 ? (
          <p className="px-4 py-8 text-center text-sm text-ink-500">
            Ningún proyecto coincide con la búsqueda.
          </p>
        ) : (
          <ProjectsTable projects={visibleProjects} />
        )}
      </Card>

      <NewProjectModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreated={reload}
      />
    </div>
  );
}
