import type { OrganizationMemberSummary, UserSummary } from '../types/auth';
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

export function canManageOrganizationUsers(
  caller: UserSummary | null,
  members: OrganizationMemberSummary[],
): boolean {
  if (!caller) {
    return false;
  }
  if (caller.platformRole === 'ADMIN') {
    return true;
  }
  return members.some(
    (member) =>
      member.userId === caller.id &&
      member.active &&
      member.permissions.includes('MANAGE_ORGANIZATION'),
  );
}
