import { useId, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { FilterSelect } from '../components/ui/FilterSelect';
import { SearchField } from '../components/ui/SearchField';
import { PlusIcon } from '../components/ui/icons';
import { DeleteProjectDialog } from '../components/projects/DeleteProjectDialog';
import { EditProjectModal } from '../components/projects/EditProjectModal';
import { NewProjectModal } from '../components/projects/NewProjectModal';
import { ProjectMembersModal } from '../components/projects/ProjectMembersModal';
import { ProjectsTable } from '../components/projects/ProjectsTable';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import type { ProjectSummary } from '../types/project';

const ALL_ORGANIZATIONS = 'all';

export function ProjectsPage() {
  const searchId = useId();
  const { projects, isLoading, error, reload, selectProject } = useActiveProject();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [organizationId, setOrganizationId] = useState(ALL_ORGANIZATIONS);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [viewing, setViewing] = useState<ProjectSummary | null>(null);
  const [editing, setEditing] = useState<ProjectSummary | null>(null);
  const [deleting, setDeleting] = useState<ProjectSummary | null>(null);

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
          <SearchField
            id={searchId}
            label="Buscar proyectos"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar proyectos"
            wrapperClassName="min-w-56 flex-1"
          />
          <FilterSelect
            label="Filtrar por organización"
            value={organizationId}
            options={[
              { value: ALL_ORGANIZATIONS, label: 'Todas las organizaciones' },
              ...organizations.map((organization) => ({
                value: organization.id,
                label: organization.name,
              })),
            ]}
            onChange={setOrganizationId}
          />
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
          <ProjectsTable
            projects={visibleProjects}
            isPlatformAdmin={user?.platformRole === 'ADMIN'}
            onView={setViewing}
            onEdit={setEditing}
            onDelete={setDeleting}
            onManageMembers={(project) => navigate(`/projects/${project.id}/team`)}
            onViewVersions={(project) => {
              selectProject(project.id);
              navigate(`/projects/${project.id}/versions`);
            }}
          />
        )}
      </Card>

      <NewProjectModal
        open={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onCreated={reload}
      />

      <EditProjectModal
        project={editing}
        onClose={() => setEditing(null)}
        onUpdated={reload}
      />

      <DeleteProjectDialog
        project={deleting}
        onClose={() => setDeleting(null)}
        onDeleted={reload}
      />

      <ProjectMembersModal project={viewing} onClose={() => setViewing(null)} />
    </div>
  );
}
