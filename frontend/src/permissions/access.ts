import type { PlatformRole } from '../types/auth';
import type { ProjectMemberType } from '../types/project';

export interface AccessUser {
  id?: string;
  platformRole?: PlatformRole;
}

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
