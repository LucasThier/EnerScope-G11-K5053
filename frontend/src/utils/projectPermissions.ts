import type { ProjectMemberType } from '../types/project';

export function canManageProject(
  role: ProjectMemberType | null | undefined,
  isPlatformAdmin: boolean,
): boolean {
  return isPlatformAdmin || role === 'ADMIN';
}
