import type { VersionSummary } from '../types/diagram';

export interface VersionTreeNode {
  version: VersionSummary;
  children: VersionTreeNode[];
}

function byCreatedAtAsc(a: VersionSummary, b: VersionSummary): number {
  return new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime();
}

function knownParentId(version: VersionSummary, byId: Map<string, VersionSummary>): string | null {
  const parentId = version.parentVersionId;
  if (parentId === null || parentId === version.id || !byId.has(parentId)) {
    return null;
  }
  return parentId;
}

function childrenByParentOf(versions: VersionSummary[]): Map<string, VersionSummary[]> {
  const byId = new Map(versions.map((version) => [version.id, version]));
  const childrenByParent = new Map<string, VersionSummary[]>();
  for (const version of versions) {
    const parentId = knownParentId(version, byId);
    if (parentId === null) {
      continue;
    }
    const siblings = childrenByParent.get(parentId) ?? [];
    siblings.push(version);
    childrenByParent.set(parentId, siblings);
  }
  return childrenByParent;
}

export function buildVersionTree(versions: VersionSummary[]): VersionTreeNode[] {
  const byId = new Map(versions.map((version) => [version.id, version]));
  const childrenByParent = childrenByParentOf(versions);
  const rootVersions = versions.filter((version) => knownParentId(version, byId) === null);

  const visited = new Set<string>();

  function buildNode(version: VersionSummary, ancestors: Set<string>): VersionTreeNode {
    visited.add(version.id);
    const nextAncestors = new Set(ancestors);
    nextAncestors.add(version.id);
    const children = (childrenByParent.get(version.id) ?? [])
      .filter((child) => !nextAncestors.has(child.id))
      .sort(byCreatedAtAsc)
      .map((child) => buildNode(child, nextAncestors));
    return { version, children };
  }

  const tree = rootVersions.sort(byCreatedAtAsc).map((version) => buildNode(version, new Set()));

  const unreached = versions.filter((version) => !visited.has(version.id)).sort(byCreatedAtAsc);
  for (const version of unreached) {
    tree.push(buildNode(version, new Set()));
  }

  return tree;
}

export function getNonRootVersionIds(versions: VersionSummary[]): string[] {
  const byId = new Map(versions.map((version) => [version.id, version]));
  return versions
    .filter((version) => knownParentId(version, byId) !== null)
    .map((version) => version.id);
}

export function getDescendants(versions: VersionSummary[], versionId: string): VersionSummary[] {
  const childrenByParent = childrenByParentOf(versions);
  const descendants: VersionSummary[] = [];
  const visited = new Set<string>([versionId]);
  const queue: string[] = [versionId];

  while (queue.length > 0) {
    const currentId = queue.shift() as string;
    for (const child of childrenByParent.get(currentId) ?? []) {
      if (visited.has(child.id)) {
        continue;
      }
      visited.add(child.id);
      descendants.push(child);
      queue.push(child.id);
    }
  }

  return descendants.sort(byCreatedAtAsc);
}
