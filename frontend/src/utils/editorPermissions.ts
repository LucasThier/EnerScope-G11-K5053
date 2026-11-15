import { canBranchFromParent, findVersionAccess } from './versionAccessRules';
import type { ProjectAccess } from '../types/versionAccess';

export interface EditorGates {
  /** Create, move, rename, edit and delete nodes. */
  canEdit: boolean;
  /** Create and delete connections. */
  canEditConnections: boolean;
  /** Create the first (root) version of the project. */
  canCreateRoot: boolean;
  canBranchFrom: (parentVersionId: string) => boolean;
  /** True when nothing can be changed (no access data, failed, loading or no permission). */
  readOnly: boolean;
}

/**
 * Everything the editor may do, derived from `/projects/{id}/access`.
 * Deny by default: no access data, a failed load or a load in flight give false everywhere.
 */
export function editorGates(
  access: ProjectAccess | null | undefined,
  versionId: string | null | undefined,
  failed: boolean,
  loading: boolean,
): EditorGates {
  const usable = access ?? null;
  if (!usable || failed || loading) {
    return {
      canEdit: false,
      canEditConnections: false,
      canCreateRoot: false,
      canBranchFrom: () => false,
      readOnly: true,
    };
  }
  const version = findVersionAccess(usable, versionId);
  const canEdit = version?.canEdit === true;
  const canEditConnections = version?.canEditConnections === true;
  return {
    canEdit,
    canEditConnections,
    canCreateRoot: usable.canCreateRootVersions === true,
    canBranchFrom: (parentVersionId) => canBranchFromParent(usable, parentVersionId),
    readOnly: !canEdit && !canEditConnections,
  };
}
