import { RemoveMemberDialog } from '../members/RemoveMemberDialog';
import { projectsApi } from '../../api/projects';
import { useAuth } from '../../hooks/useAuth';
import type { ProjectMember } from '../../types/project';

interface RemoveProjectMemberDialogProps {
  projectId: string;
  projectName: string;
  member: ProjectMember | null;
  onClose: () => void;
  onRemoved: (removedSelf: boolean) => Promise<void>;
}

export function RemoveProjectMemberDialog({
  projectId,
  projectName,
  member,
  onClose,
  onRemoved,
}: RemoveProjectMemberDialogProps) {
  const { user: caller } = useAuth();
  const isSelf = member !== null && caller !== null && member.userId === caller.id;
  const isPlatformAdmin = caller?.platformRole === 'ADMIN';

  async function handleConfirm() {
    if (!member) {
      return;
    }
    await projectsApi.removeMember(projectId, member.id);
    onClose();
    await onRemoved(isSelf);
  }

  return (
    <RemoveMemberDialog
      open={member !== null}
      title="Quitar del proyecto"
      onClose={onClose}
      onConfirm={handleConfirm}
      description={
        <>
          <p className="text-sm text-ink-700">
            Se va a quitar a{' '}
            <span className="font-semibold text-ink-800">
              {member?.firstName} {member?.lastName}
            </span>{' '}
            de <span className="font-semibold text-ink-800">{projectName}</span>.
          </p>
          <p className="text-sm text-ink-700">
            Pierde el acceso a las versiones y a los resultados de este proyecto.
          </p>
          <p className="text-sm text-ink-500">
            La cuenta de plataforma y la membresía en la organización no se tocan.
          </p>
        </>
      }
      warning={
        !isSelf
          ? null
          : isPlatformAdmin
            ? 'Estás quitando tu propia membresía. Dejás de figurar como integrante de este ' +
              'proyecto; tu acceso continúa por tu rol de administrador de plataforma.'
            : 'Estás quitando tu propia membresía: vas a perder el acceso a este proyecto en ' +
              'cuanto confirmes. Solo otro administrador del proyecto puede volver a agregarte.'
      }
    />
  );
}
