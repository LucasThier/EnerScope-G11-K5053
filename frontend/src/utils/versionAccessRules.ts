import { specForType } from '../components/editor/nodeCatalog';
import type { NodeType, Vertical } from '../types/diagram';
import type { ProjectAccess, VersionAccess } from '../types/versionAccess';

export function findVersionAccess(
  access: ProjectAccess | null | undefined,
  versionId: string | null | undefined,
): VersionAccess | null {
  if (!access || !versionId) {
    return null;
  }
  return access.versions.find((version) => version.versionId === versionId) ?? null;
}

export function canMergeIntoParent(
  access: ProjectAccess | null | undefined,
  parentVersionId: string | null | undefined,
): boolean {
  return findVersionAccess(access, parentVersionId)?.canManage ?? false;
}

export function canBranchFromParent(
  access: ProjectAccess | null | undefined,
  parentVersionId: string | null | undefined,
): boolean {
  return findVersionAccess(access, parentVersionId)?.canEdit ?? false;
}

export const UNKNOWN_VERTICAL = 'UNKNOWN';

export function scenarioVerticals(nodes: { type: string }[]): (Vertical | typeof UNKNOWN_VERTICAL)[] {
  const found = new Set<Vertical | typeof UNKNOWN_VERTICAL>();
  for (const node of nodes) {
    found.add(specForType(node.type as NodeType)?.vertical ?? UNKNOWN_VERTICAL);
  }
  return [...found];
}

export function uneditableVerticals(
  nodes: { type: string }[],
  editable: Vertical[],
): (Vertical | typeof UNKNOWN_VERTICAL)[] {
  return scenarioVerticals(nodes).filter(
    (vertical) => vertical === UNKNOWN_VERTICAL || !editable.includes(vertical),
  );
}
