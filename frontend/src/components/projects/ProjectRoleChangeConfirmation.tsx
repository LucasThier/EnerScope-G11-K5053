import { ChangeRoleConfirmation } from '../members/ChangeRoleConfirmation';
import { EDITOR_LIMITS, EXECUTIVE_LIMITS, PROJECT_MEMBER_TYPE_LABELS } from './projectMemberTypeLabels';
import { useAuth } from '../../hooks/useAuth';
import { isPlatformAdmin } from '../../permissions/access';
import type { PendingRoleChange, RoleChangeError } from '../../hooks/useMemberRoleChange';
import type { ProjectMember } from '../../types/project';

interface ProjectRoleChangeConfirmationProps {
  change: PendingRoleChange<ProjectMember>;
  projectName: string;
  error: RoleChangeError | null;
  submitting: boolean;
  onCancel: () => void;
  onConfirm: () => void;
}

export function ProjectRoleChangeConfirmation({
  change,
  projectName,
  ...rest
}: ProjectRoleChangeConfirmationProps) {
  const { user: caller } = useAuth();
  const limits = change.memberType === 'EXECUTIVE' ? EXECUTIVE_LIMITS : EDITOR_LIMITS;
  const losesAccess = change.isSelf && !isPlatformAdmin(caller);

  return (
    <ChangeRoleConfirmation
      change={change}
      roleLabels={PROJECT_MEMBER_TYPE_LABELS}
      entityName={projectName}
      consequence={
        losesAccess
          ? `${limits} Vas a dejar de ver esta pantalla en cuanto confirmes.`
          : limits
      }
      lastAdminWarning={
        `${change.isSelf ? 'Sos' : 'Es'} el único administrador del proyecto. El proyecto ` +
        'no puede quedarse sin administrador: asigná primero el rol de Administrador a otra ' +
        'persona.'
      }
      {...rest}
    />
  );
}
