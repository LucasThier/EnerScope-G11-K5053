import { projectDestination } from './workspaceRoutes';

interface ProjectRef {
  id: string;
}

export function resolveActiveProject<T extends ProjectRef>(
  projects: T[],
  routeProjectId: string | null,
  storedProjectId: string | null,
): T | null {
  if (projects.length === 0) {
    return null;
  }
  const byId = (id: string | null) => projects.find((project) => project.id === id) ?? null;
  return routeProjectId ? byId(routeProjectId) : byId(storedProjectId) ?? projects[0];
}

export function projectRouteRedirect(
  projects: ProjectRef[],
  routeProjectId: string,
  storedProjectId: string | null,
  rest: string,
): string | null {
  if (projects.some((project) => project.id === routeProjectId)) {
    return null;
  }
  const fallback = resolveActiveProject(projects, null, storedProjectId);
  return fallback ? projectDestination(fallback.id, rest) : '/projects';
}

export function versionsIndexRedirect(activeProjectId: string | null): string | null {
  return activeProjectId ? `/projects/${encodeURIComponent(activeProjectId)}/versions` : null;
}

export function isProjectAccessLoss(status: number | null): boolean {
  return status === 403 || status === 404;
}
