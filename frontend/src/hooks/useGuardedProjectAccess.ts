import { useEffect } from 'react';
import { useActiveProject } from './useActiveProject';
import { useProjectAccessCache } from './useProjectAccessCache';
import { accessFailureMessage } from '../utils/versionAccessRules';
import { projectAccessView } from '../utils/projectAccessState';
import type { ProjectAccess } from '../types/versionAccess';

interface GuardedProjectAccess {
  access: ProjectAccess | null;
  loading: boolean;
  failure: string | null;
  retry: () => Promise<void>;
}

export function useGuardedProjectAccess(projectId: string | undefined): GuardedProjectAccess {
  const { reload: reloadProjects } = useActiveProject();
  const { state, ensure, reload } = useProjectAccessCache();
  const { access, loading, error, forbidden } = projectAccessView(state, projectId);

  // Asks for the project on mount, on a project change and after an invalidation
  // (`generation`); a fresh cached answer makes this a no-op.
  useEffect(() => {
    if (projectId) {
      ensure(projectId);
    }
  }, [projectId, state.generation, ensure]);

  useEffect(() => {
    if (forbidden) {
      void reloadProjects();
    }
  }, [forbidden, reloadProjects]);

  const retry = async () => {
    if (projectId) {
      await reload(projectId);
    }
  };

  return { access, loading, failure: accessFailureMessage(error, forbidden), retry };
}
