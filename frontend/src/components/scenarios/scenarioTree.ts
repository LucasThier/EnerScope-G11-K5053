import type { VersionSummary } from '../../types/diagram';

export interface ScenarioRow {
  version: VersionSummary;
  /** Distance from the root scenario; drives the indentation. */
  depth: number;
}

const byCreation = (a: VersionSummary, b: VersionSummary) =>
  (a.createdAt ?? '').localeCompare(b.createdAt ?? '');

/**
 * Orders versions as their branching tree: each root (a version without a
 * parent in the list) followed by its branches, depth first, siblings oldest
 * first — the order a reader expects when "Ampliación" was branched off "Base".
 */
export function orderAsTree(versions: VersionSummary[]): ScenarioRow[] {
  const ids = new Set(versions.map((v) => v.id));
  const children = new Map<string, VersionSummary[]>();
  const roots: VersionSummary[] = [];
  for (const version of versions) {
    if (version.parentVersionId && ids.has(version.parentVersionId)) {
      const list = children.get(version.parentVersionId) ?? [];
      list.push(version);
      children.set(version.parentVersionId, list);
    } else {
      roots.push(version);
    }
  }
  const rows: ScenarioRow[] = [];
  const visit = (version: VersionSummary, depth: number) => {
    rows.push({ version, depth });
    [...(children.get(version.id) ?? [])].sort(byCreation).forEach((child) => visit(child, depth + 1));
  };
  [...roots].sort(byCreation).forEach((root) => visit(root, 0));
  return rows;
}

/** Every version branched, directly or not, from `versionId`. */
export function descendantsOf(versionId: string, versions: VersionSummary[]): VersionSummary[] {
  const result: VersionSummary[] = [];
  const pending = [versionId];
  while (pending.length > 0) {
    const current = pending.pop();
    for (const version of versions) {
      if (version.parentVersionId === current) {
        result.push(version);
        pending.push(version.id);
      }
    }
  }
  return result;
}
