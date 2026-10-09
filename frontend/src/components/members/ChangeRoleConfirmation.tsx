import type { ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import type {
  PendingRoleChange,
  RoleChangeError,
  RoleChangeMember,
} from '../../hooks/useMemberRoleChange';

interface ChangeRoleConfirmationProps<M extends RoleChangeMember> {
  change: PendingRoleChange<M>;
  roleLabels: Record<M['memberType'], string>;
  entityName: string;
  consequence: ReactNode;
  lastAdminWarning: ReactNode;
  error: RoleChangeError | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function ChangeRoleConfirmation<M extends RoleChangeMember>({
  change,
  roleLabels,
  entityName,
  consequence,
  lastAdminWarning,
  error,
  submitting,
  onCancel,
  onConfirm,
}: ChangeRoleConfirmationProps<M>) {
  const { member, memberType, isSelf, isLastAdmin } = change;

  return (
    <div className="flex flex-col gap-4">
      {error && <Alert tone="error">{error.message}</Alert>}

      <p className="text-sm text-ink-700">
        {isSelf ? (
          'Vas a pasar tu rol'
        ) : (
          <>
            Se va a pasar el rol de{' '}
            <span className="font-semibold text-ink-800">
              {member.firstName} {member.lastName}
            </span>
          </>
        )}{' '}
        en <span className="font-semibold text-ink-800">{entityName}</span> a{' '}
        <span className="font-semibold text-ink-800">{roleLabels[memberType]}</span>.
      </p>
      <p className="text-sm text-ink-700">{consequence}</p>

      {isLastAdmin && <Alert tone="error">{lastAdminWarning}</Alert>}

      <div className="mt-2 flex justify-end gap-2">
        <Button type="button" variant="ghost" onClick={onCancel} disabled={submitting}>
          Volver
        </Button>
        <Button type="button" onClick={onConfirm} loading={submitting}>
          Cambiar rol
        </Button>
      </div>
    </div>
  );
}
