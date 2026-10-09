import { ChangeRoleConfirmation } from '../members/ChangeRoleConfirmation';
import { MEMBER_TYPE_LABELS } from './memberTypeLabels';
import type { PendingRoleChange, RoleChangeError } from '../../hooks/useMemberRoleChange';
import type { OrganizationMemberSummary } from '../../types/auth';

interface OrganizationRoleChangeConfirmationProps {
  change: PendingRoleChange<OrganizationMemberSummary>;
  organizationName: string;
  error: RoleChangeError | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function OrganizationRoleChangeConfirmation({
  change,
  organizationName,
  ...rest
}: OrganizationRoleChangeConfirmationProps) {
  const label = MEMBER_TYPE_LABELS[change.memberType];
  return (
    <ChangeRoleConfirmation
      change={change}
      roleLabels={MEMBER_TYPE_LABELS}
      entityName={organizationName}
      consequence={
        `Pasar a ${label} hace perder el acceso a la pantalla de Gestión de organización. ` +
        'No cambia las membresías en los proyectos.'
      }
      lastAdminWarning={
        `${change.isSelf ? 'Sos' : 'Es'} el único propietario de la organización. Si se ` +
        'confirma, la organización queda sin propietarios y solo un administrador de ' +
        'plataforma puede volver a asignar uno.'
      }
      {...rest}
    />
  );
}
