import type { OrganizationMemberSummary } from '../types/auth';
import type { ProjectMember } from '../types/project';

export const LAST_OWNER_REASON = 'Es la única persona con rol Propietario.';

export const LAST_PROJECT_ADMIN_REASON = 'Es la única persona con rol Administrador.';

export function lastOwnerReason(
  members: OrganizationMemberSummary[],
  member: OrganizationMemberSummary,
): string | null {
  if (member.memberType !== 'OWNER' || !member.active) {
    return null;
  }
  const owners = members.filter((other) => other.memberType === 'OWNER' && other.active);
  return owners.length <= 1 ? LAST_OWNER_REASON : null;
}

export function lastProjectAdminReason(
  members: ProjectMember[],
  member: ProjectMember,
): string | null {
  if (member.memberType !== 'ADMIN' || !member.active) {
    return null;
  }
  const admins = members.filter((other) => other.memberType === 'ADMIN' && other.active);
  return admins.length <= 1 ? LAST_PROJECT_ADMIN_REASON : null;
}
