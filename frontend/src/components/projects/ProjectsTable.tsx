import type { ProjectSummary } from '../../types/project';
import { EyeIcon, PencilIcon, TeamIcon, TrashIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import { formatDate } from '../../utils/date';

interface ProjectsTableProps {
  projects: ProjectSummary[];
  isPlatformAdmin: boolean;
  onView: (project: ProjectSummary) => void;
  onEdit: (project: ProjectSummary) => void;
  onDelete: (project: ProjectSummary) => void;
  onManageMembers: (project: ProjectSummary) => void;
}

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 text-sm text-ink-700 align-top';

export function ProjectsTable({
  projects,
  isPlatformAdmin,
  onView,
  onEdit,
  onDelete,
  onManageMembers,
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
            <th scope="col" className={`${headerCell} text-right`}>
              Integrantes
            </th>
            <th scope="col" className={headerCell}>
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
              <td className={`${bodyCell} font-semibold text-ink-800`}>{project.name}</td>
              <td className={bodyCell}>{project.organizationName}</td>
              <td className={`${bodyCell} max-w-md`}>
                <span className="line-clamp-2">{project.description}</span>
              </td>
              <td className={`${bodyCell} text-right tabular-nums`}>{project.memberCount}</td>
              <td className={`${bodyCell} whitespace-nowrap`}>{formatDate(project.lastModified)}</td>
              <td className={`${bodyCell} text-right`}>
                <div className="flex justify-end gap-1">
                  <RowButton
                    label={`Ver integrantes de ${project.name}`}
                    onClick={() => onView(project)}
                  >
                    <EyeIcon className="h-4 w-4" />
                  </RowButton>
                  {(isPlatformAdmin || project.myRole === 'ADMIN') && (
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

