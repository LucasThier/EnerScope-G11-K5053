import type { ProjectSummary } from '../../types/project';
import { BranchIcon, EyeIcon, PencilIcon, TeamIcon, TrashIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import { formatDate } from '../../utils/date';
import { canManageProject } from '../../utils/projectPermissions';

interface ProjectsTableProps {
  projects: ProjectSummary[];
  isPlatformAdmin: boolean;
  onView: (project: ProjectSummary) => void;
  onEdit: (project: ProjectSummary) => void;
  onDelete: (project: ProjectSummary) => void;
  onManageMembers: (project: ProjectSummary) => void;
  onViewVersions: (project: ProjectSummary) => void;
}

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 h-14 align-middle text-sm text-ink-700';

export function ProjectsTable({
  projects,
  isPlatformAdmin,
  onView,
  onEdit,
  onDelete,
  onManageMembers,
  onViewVersions,
}: ProjectsTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[56rem] border-collapse">
        <thead>
          <tr className="border-b border-ink-100">
            <th scope="col" className={headerCell}>
              Proyecto
            </th>
            <th scope="col" className={headerCell}>
              Organización
            </th>
            <th scope="col" className={headerCell}>
              Descripción
            </th>
            <th scope="col" className={`${headerCell} text-center`}>
              Integrantes
            </th>
            <th scope="col" className={`${headerCell} whitespace-nowrap`}>
              Actualizado
            </th>
            <th scope="col" className={`${headerCell} text-right`}>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {projects.map((project) => (
            <tr key={project.id}>
              <td
                className={`${bodyCell} max-w-[14rem] truncate font-semibold text-ink-800`}
                title={project.name}
              >
                {project.name}
              </td>
              <td className={`${bodyCell} max-w-[12rem] truncate`} title={project.organizationName}>
                {project.organizationName}
              </td>
              <td className={`${bodyCell} max-w-[20rem] truncate`} title={project.description}>
                {project.description}
              </td>
              <td className={`${bodyCell} text-center tabular-nums`}>{project.memberCount}</td>
              <td className={`${bodyCell} whitespace-nowrap`}>{formatDate(project.lastModified)}</td>
              <td className={`${bodyCell} text-right`}>
                <div className="flex justify-end gap-1">
                  <RowButton
                    label={`Ver integrantes de ${project.name}`}
                    onClick={() => onView(project)}
                  >
                    <EyeIcon className="h-4 w-4" />
                  </RowButton>
                  <RowButton
                    label={`Ver versiones del proyecto ${project.name}`}
                    title="Ver versiones"
                    onClick={() => onViewVersions(project)}
                  >
                    <BranchIcon className="h-4 w-4" />
                  </RowButton>
                  {canManageProject(project.myRole, isPlatformAdmin) && (
                    <RowButton
                      label={`Administrar los integrantes de ${project.name}`}
                      title="Administrar integrantes"
                      onClick={() => onManageMembers(project)}
                    >
                      <TeamIcon className="h-4 w-4" />
                    </RowButton>
                  )}
                  <RowButton
                    label={`Editar ${project.name}`}
                    onClick={() => onEdit(project)}
                  >
                    <PencilIcon className="h-4 w-4" />
                  </RowButton>
                  <RowButton
                    label={`Eliminar ${project.name}`}
                    onClick={() => onDelete(project)}
                  >
                    <TrashIcon className="h-4 w-4" />
                  </RowButton>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

