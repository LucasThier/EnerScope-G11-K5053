import { canManageProject as resolveCanManageProject } from '../permissions/access';
import type { ProjectMemberType } from '../types/project';

export function canManageProject(
  role: ProjectMemberType | null | undefined,
  isPlatformAdmin: boolean,
): boolean {
  return resolveCanManageProject(role, isPlatformAdmin);
}
