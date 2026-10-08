import type { UserListItem } from '../../types/auth';
import { useAuth } from '../../hooks/useAuth';
import { InfoIcon, LockIcon, TrashIcon, UndoIcon } from '../ui/icons';
import { RoleBadge } from '../ui/RoleBadge';
import { RowButton } from '../ui/RowButton';

interface UsersTableProps {
  users: UserListItem[];
  onViewDetail: (user: UserListItem) => void;
  onChangeRole: (user: UserListItem) => void;
  onDeactivate: (user: UserListItem) => void;
  onReactivate: (user: UserListItem) => Promise<void>;
}

const headerCell = 'px-4 py-3 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-4 py-3 text-sm text-ink-700 align-top';

export function UsersTable({
  users,
  onViewDetail,
  onChangeRole,
  onDeactivate,
  onReactivate,
}: UsersTableProps) {
  const { user: currentUser } = useAuth();
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[56rem] border-collapse">
        <thead>
          <tr className="border-b border-ink-100">
            <th scope="col" className={headerCell}>
              Nombre
            </th>
            <th scope="col" className={headerCell}>
              Email
            </th>
            <th scope="col" className={headerCell}>
              Puesto
            </th>
            <th scope="col" className={headerCell}>
              Rol
            </th>
            <th scope="col" className={`${headerCell} text-right`}>
              Organizaciones
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
          {users.map((user) => (
            <tr key={user.id}>
              <td className={`${bodyCell} whitespace-nowrap font-semibold text-ink-800`}>
                {user.firstName} {user.lastName}
                {user.id === currentUser?.id && (
                  <span className="ml-2 text-xs font-normal text-ink-500">Tú</span>
                )}
              </td>
              <td className={bodyCell}>{user.mail}</td>
              <td className={bodyCell}>{user.jobTitle ?? '—'}</td>
              <td className={bodyCell}>
                <RoleBadge role={user.platformRole} />
              </td>
              <td className={`${bodyCell} text-right tabular-nums`}>
                {user.organizationCount === 0 ? (
                  <span className="text-ink-500">Sin organización</span>
                ) : (
                  user.organizationCount
                )}
              </td>
              <td className={`${bodyCell} whitespace-nowrap`}>
                {user.active ? (
                  <span className="text-ink-700">Activo</span>
                ) : (
                  <span className="text-ink-500">Inactivo</span>
                )}
              </td>
              <td className={`${bodyCell} text-right`}>
                <div className="flex justify-end gap-1">
                  <RowButton
                    label={`Ver el detalle de ${user.firstName} ${user.lastName}`}
                    title="Ver detalle"
                    onClick={() => onViewDetail(user)}
                  >
                    <InfoIcon className="h-4 w-4" />
                  </RowButton>
                  <RowButton
                    label={`Cambiar el rol de ${user.firstName} ${user.lastName}`}
                    title="Cambiar rol"
                    onClick={() => onChangeRole(user)}
                  >
                    <LockIcon className="h-4 w-4" />
                  </RowButton>
                  {user.active ? (
                    <RowButton
                      label={`Dar de baja a ${user.firstName} ${user.lastName}`}
                      title="Dar de baja"
                      onClick={() => onDeactivate(user)}
                    >
                      <TrashIcon className="h-4 w-4" />
                    </RowButton>
                  ) : (
                    <RowButton
                      label={`Reactivar a ${user.firstName} ${user.lastName}`}
                      title="Reactivar"
                      onClick={() => void onReactivate(user)}
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
