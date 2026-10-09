import { useCallback, useEffect, useState } from 'react';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import type { OrganizationSummary } from '../types/auth';

interface UseOwnedOrganizations {
  owned: OrganizationSummary[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
}

export function useOwnedOrganizations(): UseOwnedOrganizations {
  const [owned, setOwned] = useState<OrganizationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await organizationsApi.owned();
      setOwned(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar tus organizaciones'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  return { owned, loading, error, reload };
}
