export const MERGE_BLOCKED_MESSAGE = 'Resolvé los conflictos pendientes antes de mergear';

export function isMergeBlocked(pendingConflicts: number): boolean {
  return pendingConflicts > 0;
}
