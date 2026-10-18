import { Alert } from '../ui/Alert';
import { controlClasses } from '../ui/controlClasses';
import { TrashIcon } from '../ui/icons';
import { RowButton } from '../ui/RowButton';
import type { RoleChangeError } from '../../hooks/useMemberRoleChange';

export interface MemberRow {
  id: string;
  userId: string;
  userMail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  active: boolean;
  memberType: string;
}

interface MembersTableProps<M extends MemberRow> {
  members: M[];
  roleLabels: Record<M['memberType'], string>;
  inactiveLabel?: string;
  inactiveTitle?: string;
  removeLabel?: (fullName: string) => string;
  changeRoleLabel?: (fullName: string) => string;
  onRemove?: (member: M) => void;
  onChangeRole?: (member: M, memberType: M['memberType']) => void;
  disabledReason?: (member: M) => string | null;
  busyMemberId?: string | null;
  roleError?: RoleChangeError | null;
}

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 align-top text-sm text-ink-700';

export function MembersTable<M extends MemberRow>({
  members,
  roleLabels,
  inactiveLabel = 'Inactivo',
  inactiveTitle,
  removeLabel = (fullName) => `Quitar a ${fullName}`,
  changeRoleLabel = (fullName) => `Cambiar el rol de ${fullName}`,
  onRemove,
  onChangeRole,
  disabledReason,
  busyMemberId = null,
  roleError = null,
}: MembersTableProps<M>) {
  const columnCount = onRemove ? 5 : 4;
  const roles = Object.keys(roleLabels) as M['memberType'][];

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse">
        <thead>
          <tr className="border-b border-ink-100">
            <th scope="col" className={`${headerCell} whitespace-nowrap`}>
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
            {onRemove && (
              <th scope="col" className={`${headerCell} text-right`}>
                Acciones
              </th>
            )}
          </tr>
        </thead>
        <tbody className="divide-y divide-ink-100">
          {members.map((member) => {
            const fullName = `${member.firstName} ${member.lastName}`;
            const reason = disabledReason?.(member) ?? null;
            const reasonId = `motivo-${member.id}`;
            return [
              <tr key={member.id}>
                <td className={`${bodyCell} font-semibold text-ink-800`}>
                  {fullName}
                  {!member.active && (
                    <span
                      className="ml-2 text-xs font-normal text-ink-500"
                      title={inactiveTitle}
                    >
                      {inactiveLabel}
                    </span>
                  )}
                </td>
                <td className={bodyCell}>{member.userMail}</td>
                <td className={bodyCell}>{member.jobTitle ?? '—'}</td>
                <td className={`${bodyCell} whitespace-nowrap`}>
                  {onChangeRole ? (
                    <select
                      value={member.memberType}
                      onChange={(e) => onChangeRole(member, e.target.value as M['memberType'])}
                      disabled={busyMemberId === member.id || reason !== null}
                      aria-label={changeRoleLabel(fullName)}
                      aria-describedby={reason ? reasonId : undefined}
                      title={reason ?? changeRoleLabel(fullName)}
                      className={controlClasses}
                    >
                      {roles.map((type) => (
                        <option key={type} value={type}>
                          {roleLabels[type]}
                        </option>
                      ))}
                    </select>
                  ) : (
                    roleLabels[member.memberType as M['memberType']]
                  )}
                  {reason && (
                    <p id={reasonId} className="mt-1 max-w-56 whitespace-normal text-xs text-ink-500">
                      {reason}
                    </p>
                  )}
                </td>
                {onRemove && (
                  <td className={`${bodyCell} text-right`}>
                    <RowButton
                      label={reason ? `${removeLabel(fullName)} — ${reason}` : removeLabel(fullName)}
                      title={reason ?? removeLabel(fullName)}
                      disabled={reason !== null}
                      onClick={() => onRemove(member)}
                    >
                      <TrashIcon className="h-4 w-4" />
                    </RowButton>
                  </td>
                )}
              </tr>,
              roleError?.memberId === member.id ? (
                <tr key={`${member.id}-error`}>
                  <td colSpan={columnCount} className="px-3 pb-3">
                    <Alert tone="error">{roleError.message}</Alert>
                  </td>
                </tr>
              ) : null,
            ];
          })}
        </tbody>
      </table>
    </div>
  );
}
