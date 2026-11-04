import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { organizationsApi } from '../api/organizations';
import { getErrorMessage } from '../api/errors';
import type { OrganizationSummary } from '../types/auth';
import { useAuth } from './useAuth';
import { MyOrganizationsContext, type OrganizationMembership } from './useMyOrganizations';

export function MyOrganizationsProvider({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isAdmin = user?.platformRole === 'ADMIN';
  const [organizations, setOrganizations] = useState<OrganizationSummary[]>([]);
  const [mine, setMine] = useState<OrganizationSummary[]>([]);
  const [owned, setOwned] = useState<OrganizationSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [mineRes, ownedRes, allRes] = await Promise.all([
        organizationsApi.list({ mine: true }),
        organizationsApi.owned(),
        isAdmin ? organizationsApi.list() : Promise.resolve(null),
      ]);
      const mineList = mineRes.data.data ?? [];
      setMine(mineList);
      setOrganizations(allRes ? (allRes.data.data ?? []) : mineList);
      setOwned(ownedRes.data.data ?? []);
    } catch (err) {
      setError(getErrorMessage(err, 'No se pudieron cargar tus organizaciones'));
    } finally {
      setLoading(false);
    }
  }, [isAdmin]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const memberships = useMemo<OrganizationMembership[]>(
    () =>
      mine
        .map((organization) => ({
          id: organization.id,
          name: organization.name,
          role: organization.memberType === 'OWNER' ? ('OWNER' as const) : ('MEMBER' as const),
        }))
        .sort(
          (a, b) =>
            Number(b.role === 'OWNER') - Number(a.role === 'OWNER') ||
            a.name.localeCompare(b.name, 'es'),
        ),
    [mine],
  );

  const value = useMemo(
    () => ({
      organizations,
      owned,
      memberships,
      belongsToOrganization: memberships.length > 0,
      loading,
      error,
      reload,
    }),
    [organizations, owned, memberships, loading, error, reload],
  );

  return <MyOrganizationsContext.Provider value={value}>{children}</MyOrganizationsContext.Provider>;
}
