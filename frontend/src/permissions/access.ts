import type { PlatformRole } from '../types/auth';
import type { ProjectMember, ProjectMemberType } from '../types/project';

export type Vertical =
  | 'EXTRACTION'
  | 'TRANSPORTATION'
  | 'LIQUEFACTION'
  | 'EXPORT'
  | 'INTERNAL_CONSUMPTION';

export type VersionRoleType = 'ADMIN' | 'EDITOR' | 'SIMULATOR';

export interface VersionRoleInput {
  type: VersionRoleType;
  vertical: Vertical | null;
}

export interface AccessUser {
  id?: string;
  platformRole?: PlatformRole;
}

export const ROLE_FEATURES = {
  executive: false,
} as const;

export const EXECUTIVE_CAN_VIEW_ECONOMICS = true;

export function isPlatformAdmin(user: AccessUser | null | undefined): boolean {
  return user?.platformRole === 'ADMIN';
}

export function hasPlatformRole(
  user: AccessUser | null | undefined,
  role: PlatformRole,
): boolean {
  return user?.platformRole === role;
}

export function canManageProject(
  role: ProjectMemberType | null | undefined,
  platformAdmin: boolean,
): boolean {
  return platformAdmin || role === 'ADMIN';
}

export function canEditProjectByRole(
  role: ProjectMemberType | null | undefined,
  platformAdmin: boolean,
): boolean {
  return platformAdmin || role === 'ADMIN' || role === 'EDITOR';
}

export function canEditProjectByPermissions(
  user: AccessUser | null | undefined,
  members: ProjectMember[] | null | undefined,
): boolean {
  return (
    isPlatformAdmin(user) ||
    !!members?.some(
      (member) =>
        member.userId === user?.id && member.active && member.permissions.includes('EDIT_PROJECT'),
    )
  );
}

export interface VersionCapabilityInput {
  platformAdmin: boolean;
  projectRole: ProjectMemberType | null | undefined;
  versionRoles?: VersionRoleInput[] | null;
  executiveEnabled?: boolean;
  executiveCanViewEconomics?: boolean;
}

export interface VersionCapabilities {
  isExecutive: boolean;
  canView: boolean;
  canSimulate: boolean;
  canViewEconomics: boolean;
  canEditConnections: boolean;
  canManageTeam: boolean;
  canEditVertical: (vertical: Vertical) => boolean;
}

export function resolveVersionCapabilities(input: VersionCapabilityInput): VersionCapabilities {
  const executiveEnabled = input.executiveEnabled ?? ROLE_FEATURES.executive;
  const roles = input.versionRoles ?? [];
  const executive =
    executiveEnabled &&
    !input.platformAdmin &&
    roles.length > 0 &&
    roles.every((role) => role.type === 'SIMULATOR');

  if (executive) {
    return {
      isExecutive: true,
      canView: true,
      canSimulate: true,
      canViewEconomics: input.executiveCanViewEconomics ?? EXECUTIVE_CAN_VIEW_ECONOMICS,
      canEditConnections: false,
      canManageTeam: false,
      canEditVertical: () => false,
    };
  }

  const canEdit = canEditProjectByRole(input.projectRole, input.platformAdmin);
  return {
    isExecutive: false,
    canView: true,
    canSimulate: canEdit,
    canViewEconomics: true,
    canEditConnections: canEdit,
    canManageTeam: canManageProject(input.projectRole, input.platformAdmin),
    canEditVertical: () => canEdit,
  };
}
