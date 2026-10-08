import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { Spinner } from '../ui/Spinner';
import { useOrganizations } from '../../hooks/useOrganizations';
import { useUsers } from '../../hooks/useUsers';

interface SecondaryRow {
  label: string;
  value: number;
  emphasize?: boolean;
}

interface SummaryCardProps {
  label: string;
  value: number;
  rows: SecondaryRow[];
  loading: boolean;
  error: string | null;
  actionLabel: string;
  onAction: () => void;
}

function SummaryCard({ label, value, rows, loading, error, actionLabel, onAction }: SummaryCardProps) {
  return (
    <Card>
      <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</p>
      <div className="mt-2 min-h-9">
        {loading ? (
          <Spinner className="h-5 w-5 text-ink-400" />
        ) : error ? (
          <Alert tone="error">{error}</Alert>
        ) : (
          <>
            <p className="text-2xl font-semibold text-ink-800">{value}</p>
            <dl className="mt-3 flex flex-col gap-1">
              {rows.map((row) => (
                <div key={row.label} className="flex items-center justify-between text-sm">
                  <dt className="text-ink-500">{row.label}</dt>
                  <dd className={row.emphasize ? 'font-semibold text-ink-800' : 'text-ink-700'}>
                    {row.value}
                  </dd>
                </div>
              ))}
            </dl>
          </>
        )}
      </div>
      <button
        type="button"
        onClick={onAction}
        className="mt-4 text-sm font-semibold text-brand-800 hover:underline"
      >
        {actionLabel}
      </button>
    </Card>
  );
}

/** Platform-wide counters for admins only, computed client-side from the same
 * lists the Users and Organizations screens already load. Each card depends on
 * one hook only, so a failed GET /users never affects the Organizaciones card
 * and vice versa. */
export function PlatformSummary() {
  const navigate = useNavigate();
  const { users, loading: usersLoading, error: usersError } = useUsers();
  const { organizations, loading: organizationsLoading, error: organizationsError } =
    useOrganizations();

  const userCounts = useMemo(
    () => ({
      total: users.length,
      suspended: users.filter((user) => !user.active).length,
      withoutOrganization: users.filter((user) => user.organizationCount === 0).length,
    }),
    [users],
  );

  const organizationCounts = useMemo(
    () => ({
      active: organizations.filter((organization) => organization.active).length,
      suspended: organizations.filter((organization) => !organization.active).length,
    }),
    [organizations],
  );

  return (
    <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-2">
      <SummaryCard
        label="Usuarios"
        value={userCounts.total}
        rows={[
          { label: 'Inactivas', value: userCounts.suspended },
          {
            label: 'Sin organización',
            value: userCounts.withoutOrganization,
            emphasize: userCounts.withoutOrganization > 0,
          },
        ]}
        loading={usersLoading}
        error={usersError}
        actionLabel="Gestionar usuarios"
        onAction={() => navigate('/admin/users')}
      />
      <SummaryCard
        label="Organizaciones"
        value={organizationCounts.active}
        rows={[{ label: 'Inactivas', value: organizationCounts.suspended }]}
        loading={organizationsLoading}
        error={organizationsError}
        actionLabel="Gestionar organizaciones"
        onAction={() => navigate('/admin/organizations')}
      />
    </div>
  );
}
