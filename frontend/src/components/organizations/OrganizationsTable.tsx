import type { OrganizationSummary } from '../../types/auth';
import { EyeIcon, PencilIcon, TrashIcon, UndoIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import { formatDate } from '../../utils/date';

interface OrganizationsTableProps {
  organizations: OrganizationSummary[];
  onViewMembers: (organization: OrganizationSummary) => void;
  onRename: (organization: OrganizationSummary) => void;
  onDeactivate: (organization: OrganizationSummary) => void;
  onReactivate: (organization: OrganizationSummary) => Promise<void>;
}

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 text-sm text-ink-700 align-top';

export function OrganizationsTable({
  organizations,
  onViewMembers,
  onRename,
  onDeactivate,
  onReactivate,
}: OrganizationsTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[40rem] border-collapse">
        <thead>
          <tr className="border-b border-ink-100">
            <th scope="col" className={headerCell}>
              Organización
            </th>
            <th scope="col" className={`${headerCell} text-right`}>
              Integrantes
            </th>
            <th scope="col" className={headerCell}>
              Creada
            </th>
            <th scope="col" className={headerCell}>
              Estado
            </th>
            <th scope="col" className={`${headerCell} text-right`}>
              Acciones
            </th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {organizations.map((organization) => (
            <tr key={organization.id}>
              <td className={`${bodyCell} font-semibold text-ink-800`}>{organization.name}</td>
              <td className={`${bodyCell} text-right tabular-nums`}>
                {organization.memberCount === 0 ? (
                  <span className="text-ink-500">Sin integrantes</span>
                ) : (
                  organization.memberCount
                )}
              </td>
              <td className={`${bodyCell} whitespace-nowrap`}>
                {formatDate(organization.createdAt)}
              </td>
              <td className={`${bodyCell} whitespace-nowrap`}>
                {organization.active ? (
                  <span className="text-ink-700">Activa</span>
                ) : (
                  <span className="text-ink-500">Inactiva</span>
                )}
              </td>
              <td className={`${bodyCell} text-right`}>
                <div className="flex justify-end gap-1">
                  <RowButton
                    label={`Ver integrantes de ${organization.name}`}
                    onClick={() => onViewMembers(organization)}
                  >
                    <EyeIcon className="h-4 w-4" />
                  </RowButton>
                  <RowButton
                    label={`Renombrar ${organization.name}`}
                    onClick={() => onRename(organization)}
                  >
                    <PencilIcon className="h-4 w-4" />
                  </RowButton>
                  {organization.active ? (
                    <RowButton
                      label={`Dar de baja ${organization.name}`}
                      onClick={() => onDeactivate(organization)}
                    >
                      <TrashIcon className="h-4 w-4" />
                    </RowButton>
                  ) : (
                    <RowButton
                      label={`Reactivar ${organization.name}`}
                      onClick={() => void onReactivate(organization)}
                    >
                      <UndoIcon className="h-4 w-4" />
                    </RowButton>
                  )}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
