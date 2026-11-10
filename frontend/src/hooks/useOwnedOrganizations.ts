import type { OrganizationSummary } from '../types/auth';
import { useMyOrganizations } from './useMyOrganizations';

interface UseOwnedOrganizations {
  owned: OrganizationSummary[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

export function useOwnedOrganizations(): UseOwnedOrganizations {
  const { owned, loading, error, reload } = useMyOrganizations();
  return { owned, loading, error, reload };
}
