import { createContext, useContext } from 'react';
import type { ProjectSummary } from '../types/project';

/** Route pattern that scopes a page to one project; the URL wins over any stored preference. */
export const PROJECT_ROUTE_PATTERN = '/projects/:projectId/*';

export interface ActiveProjectContextValue {
  projects: ProjectSummary[];
  /** The project the app is currently working on, or null while loading or with no projects. */
  activeProject: ProjectSummary | null;
  isLoading: boolean;
  error: string | null;
  selectProject: (projectId: string) => void;
  reload: () => Promise<void>;
}

export const ActiveProjectContext = createContext<ActiveProjectContextValue | undefined>(undefined);

/** Access the project list and the active selection. Must be used within an {@link ActiveProjectProvider}. */
export function useActiveProject(): ActiveProjectContextValue {
  const ctx = useContext(ActiveProjectContext);
  if (!ctx) {
    throw new Error('useActiveProject must be used within an ActiveProjectProvider');
  }
  return ctx;
}
