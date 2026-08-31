import { useCallback, useEffect, useState } from 'react';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import type { OrganizationSummary } from '../types/auth';

interface UseOrganizations {
  organizations: OrganizationSummary[];
  loading: boolean;
  error: string | null;
  reload: () => Promise<void>;
  createOrganization: (name: string) => Promise<OrganizationSummary>;
}

/**
 * Loads the organizations the current user can see and exposes a create action
 * that keeps the local list in sync. Shared by the create-user and
 * organizations pages.
 */
export function useOrganizations(): UseOrganizations {
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const res = await organizationsApi.list();
      setOrganizations(res.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'Could not load organizations'));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  const createOrganization = useCallback(async (name: string): Promise<OrganizationSummary> => {
    const res = await organizationsApi.create({ name });
    const created = res.data.data;
    if (!created) {
      throw new Error(res.data.message || 'Could not create organization');
    }
    setOrganizations((prev) => [...prev, created]);
    return created;
  }, []);

  return { organizations, loading, error, reload, createOrganization };
}
