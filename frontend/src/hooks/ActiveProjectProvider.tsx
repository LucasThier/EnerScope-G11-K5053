import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMatch, useNavigate } from 'react-router-dom';
import { projectsApi } from '../api/projects';
import { getErrorMessage } from '../api/errors';
import type { ProjectSummary } from '../types/project';
import { useLocalPreference } from './useLocalPreference';
import {
  ActiveProjectContext,
  PROJECT_ROUTE_PATTERN,
  type ActiveProjectContextValue,
} from './useActiveProject';

const LAST_PROJECT_KEY = 'lastProjectId';

/**
 * Loads the caller's projects and decides which one the app is working on.
 *
 * <p>Resolution order is deliberate: a project id in the URL wins, so a shared
 * or bookmarked link always opens the project it names; otherwise the last
 * selection is restored; otherwise the first project. Project-scoped routes do
 * not exist yet, so in practice the stored preference decides today — the URL
 * branch starts working the moment those routes land, with no change here.</p>
 */
export function ActiveProjectProvider({ children }: { children: ReactNode }) {
  const [projects, setProjects] = useState<ProjectSummary[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [lastProjectId, setLastProjectId] = useLocalPreference<string | null>(
    LAST_PROJECT_KEY,
    null,
  );

  const routeMatch = useMatch(PROJECT_ROUTE_PATTERN);
  const routeProjectId = routeMatch?.params.projectId ?? null;
  const navigate = useNavigate();

  const reload = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const res = await projectsApi.list();
      setProjects(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar los proyectos'));
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const activeProject = useMemo(() => {
    if (projects.length === 0) {
      return null;
    }
    const byId = (id: string | null) => projects.find((project) => project.id === id) ?? null;
    return byId(routeProjectId) ?? byId(lastProjectId) ?? projects[0];
  }, [projects, routeProjectId, lastProjectId]);

  useEffect(() => {
    if (activeProject && activeProject.id !== lastProjectId) {
      setLastProjectId(activeProject.id);
    }
  }, [activeProject, lastProjectId, setLastProjectId]);

  const selectProject = useCallback(
    (projectId: string) => {
      setLastProjectId(projectId);
      if (routeMatch) {
        const rest = routeMatch.params['*'] ?? '';
        navigate(`/projects/${projectId}${rest ? `/${rest}` : ''}`);
      }
    },
    [routeMatch, navigate, setLastProjectId],
  );

  const value: ActiveProjectContextValue = {
    projects,
    activeProject,
    isLoading,
    error,
    selectProject,
    reload,
  };

  return <ActiveProjectContext.Provider value={value}>{children}</ActiveProjectContext.Provider>;
}
