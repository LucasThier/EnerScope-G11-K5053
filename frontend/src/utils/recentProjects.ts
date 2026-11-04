import type { ProjectSummary } from '../types/project';

function timestampOf(project: ProjectSummary): number {
  const time = new Date(project.lastModified).getTime();
  return Number.isNaN(time) ? Number.NEGATIVE_INFINITY : time;
}

export function hasReliableRecency(projects: ProjectSummary[]): boolean {
  return projects.some((project) => timestampOf(project) !== Number.NEGATIVE_INFINITY);
}

export function sortByRecency(projects: ProjectSummary[]): ProjectSummary[] {
  return [...projects].sort((a, b) => timestampOf(b) - timestampOf(a));
}
