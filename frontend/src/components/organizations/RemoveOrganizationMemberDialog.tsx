import { RemoveMemberDialog } from '../members/RemoveMemberDialog';
import { organizationsApi } from '../../api/organizations';
import { useAuth } from '../../hooks/useAuth';
import type { OrganizationMemberSummary } from '../../types/auth';

interface RemoveOrganizationMemberDialogProps {
  organizationId: string;
  organizationName: string;
  member: OrganizationMemberSummary | null;
  onClose: () => void;
  onRemoved: (removedSelf: boolean) => Promise<void>;
}

export function RemoveOrganizationMemberDialog({
  organizationId,
  organizationName,
  member,
  onClose,
  onRemoved,
}: RemoveOrganizationMemberDialogProps) {
  const { user: caller } = useAuth();
  const isSelf = member !== null && caller !== null && member.userId === caller.id;

  async function handleConfirm() {
    if (!member) {
      return;
    }
    await organizationsApi.removeMember(organizationId, member.id);
    await onRemoved(isSelf);
    onClose();
  }

  return (
    <RemoveMemberDialog
      open={member !== null}
      title="Quitar de la organización"
      onClose={onClose}
      onConfirm={handleConfirm}
      description={
        <>
          <p className="text-sm text-ink-700">
            Se va a quitar a{' '}
            <span className="font-semibold text-ink-800">
              {member?.firstName} {member?.lastName}
            </span>{' '}
            de <span className="font-semibold text-ink-800">{organizationName}</span>.
          </p>
          <p className="text-sm text-ink-700">
            También se le quitan sus membresías en los proyectos de esta organización. Si era
            el único administrador de alguno de ellos, ese proyecto queda sin administrador.
          </p>
          <p className="text-sm text-ink-500">
            La cuenta de plataforma no se toca: sigue pudiendo iniciar sesión y conserva sus
            otras organizaciones.
          </p>
        </>
      }
      warning={
        isSelf
          ? 'Estás quitando tu propia membresía: vas a perder el acceso a esta organización y ' +
            'a sus proyectos en cuanto confirmes. Solo un administrador de plataforma puede ' +
            'volver a agregarte.'
          : null
      }
    />
  );
}
