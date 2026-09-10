import type { ReactNode } from 'react';
import type { ProjectSummary } from '../../types/project';
import { EyeIcon, PencilIcon, TrashIcon } from '../ui/icons';
import { formatDate } from '../../utils/date';

interface ProjectsTableProps {
  projects: ProjectSummary[];
  onView: (project: ProjectSummary) => void;
}

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 text-sm text-ink-700 align-top';

export function ProjectsTable({ projects, onView }: ProjectsTableProps) {
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
                  <RowAction label={`Editar ${project.name}`}>
                    <PencilIcon className="h-4 w-4" />
                  </RowAction>
                  <RowAction label={`Eliminar ${project.name}`}>
                    <TrashIcon className="h-4 w-4" />
                  </RowAction>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function RowButton({
  label,
  onClick,
  children,
}: {
  label: string;
  onClick: () => void;
  children: ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={
        'inline-flex rounded-lg p-1 text-ink-500 transition-colors hover:bg-ink-50 ' +
        'hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
      }
    >
      {children}
    </button>
  );
}

function RowAction({ label, children }: { label: string; children: ReactNode }) {
  return (
    <span
      aria-disabled="true"
      title={`${label} — no disponible`}
      className="inline-flex cursor-not-allowed rounded-lg p-1 text-ink-500"
    >
      {children}
    </span>
  );
}
