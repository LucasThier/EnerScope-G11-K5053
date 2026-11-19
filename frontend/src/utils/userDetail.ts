import type { OrganizationMemberType } from '../types/auth';
import type { ProjectMemberType } from '../types/project';

const ORGANIZATION_ROLE_LABELS: Record<OrganizationMemberType, string> = {
  OWNER: 'Propietario',
  MEMBER: 'Miembro',
};

const PROJECT_ROLE_LABELS: Record<ProjectMemberType, string> = {
  ADMIN: 'Administrador',
  EDITOR: 'Editor',
  EXECUTIVE: 'Ejecutivo',
};

/** Spanish label of a role in an organization; an unknown value is shown as the backend sent it. */
export function organizationRoleLabel(memberType: OrganizationMemberType): string {
  return ORGANIZATION_ROLE_LABELS[memberType] ?? String(memberType);
}

/** Spanish label of a role in a project; an unknown value is shown as the backend sent it. */
export function projectRoleLabel(memberType: ProjectMemberType): string {
  return PROJECT_ROLE_LABELS[memberType] ?? String(memberType);
}

export function userStatusLabel(active: boolean): string {
  return active ? 'Activo' : 'Inactivo';
}

export type UserDetailAction = 'changeRole' | 'deactivate' | 'reactivate';

/** Actions offered inside the detail: the role can always change; the account toggles by state. */
export function userDetailActions(user: { active: boolean }): UserDetailAction[] {
  return ['changeRole', user.active ? 'deactivate' : 'reactivate'];
}
