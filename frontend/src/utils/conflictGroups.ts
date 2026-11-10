import type { ConflictEntityType, VersionConflict } from '../types/diagram';

export interface ConflictGroup {
  key: string;
  conflicts: VersionConflict[];
}

const ENTITY_ORDER: Record<ConflictEntityType, number> = {
  NODE: 0,
  CONNECTION: 1,
};

export function groupPendingConflicts(conflicts: VersionConflict[]): ConflictGroup[] {
  const groups = new Map<string, ConflictGroup>();
  for (const conflict of conflicts) {
    if (conflict.resolved) {
      continue;
    }
    const key = `${conflict.entityType}|${conflict.identityId}|${conflict.sourceVersionId ?? conflict.id}`;
    const group = groups.get(key) ?? { key, conflicts: [] };
    group.conflicts.push(conflict);
    groups.set(key, group);
  }
  return [...groups.values()].sort(
    (a, b) => ENTITY_ORDER[a.conflicts[0].entityType] - ENTITY_ORDER[b.conflicts[0].entityType],
  );
}
