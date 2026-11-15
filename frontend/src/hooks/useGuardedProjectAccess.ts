import { useEffect } from 'react';
import { useActiveProject } from './useActiveProject';
import { useProjectAccess } from './useProjectAccess';
import { accessFailureMessage } from '../utils/versionAccessRules';
import type { ProjectAccess } from '../types/versionAccess';

interface GuardedProjectAccess {
  access: ProjectAccess | null;
  loading: boolean;
  failure: string | null;
  retry: () => Promise<void>;
}

export function useGuardedProjectAccess(projectId: string | undefined): GuardedProjectAccess {
  const { reload: reloadProjects } = useActiveProject();
  const { access, loading, error, forbidden, reload } = useProjectAccess(projectId);

  useEffect(() => {
    if (forbidden) {
      void reloadProjects();
    }
  }, [forbidden, reloadProjects]);

  return { access, loading, failure: accessFailureMessage(error, forbidden), retry: reload };
}
