import { createContext, useContext } from 'react';
import type { ProjectAccessState } from '../utils/projectAccessState';

export interface ProjectAccessCacheValue {
  state: ProjectAccessState;
  /** Loads the access of `projectId` unless it is cached and fresh or already loading. */
  ensure: (projectId: string) => void;
  /** Always refetches `projectId`, superseding any request in flight. */
  reload: (projectId: string) => Promise<void>;
}

export const ProjectAccessCacheContext = createContext<ProjectAccessCacheValue | undefined>(
  undefined,
);

export function useProjectAccessCache(): ProjectAccessCacheValue {
  const ctx = useContext(ProjectAccessCacheContext);
  if (!ctx) {
    throw new Error('useProjectAccessCache must be used within a ProjectAccessProvider');
  }
  return ctx;
}
