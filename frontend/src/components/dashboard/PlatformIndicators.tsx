import { useMemo, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { ArrowRightIcon, BuildingIcon, FolderIcon, UsersIcon } from '../ui/icons';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useUsers } from '../../hooks/useUsers';
import type { OrganizationSummary } from '../../types/auth';

interface IndicatorProps {
  icon: ReactNode;
  label: string;
  value: number;
  loading: boolean;
  error: string | null;
  to: string;
  linkLabel: string;
}

function Indicator({ icon, label, value, loading, error, to, linkLabel }: IndicatorProps) {
  return (
    <Card padded={false} className="flex items-center gap-4 p-5">
      <span
        className="flex h-14 w-14 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-800"
        aria-hidden="true"
      >
        {icon}
      </span>
      <div className="min-w-0">
        <p className="text-sm text-ink-700">{label}</p>
        {loading ? (
          <div
            role="status"
            className="my-1 h-8 w-12 rounded-control bg-ink-100 motion-safe:animate-pulse"
          >
            <span className="sr-only">Cargando {label}</span>
          </div>
        ) : error ? (
          <Alert tone="error" className="mt-1">
            {error}
          </Alert>
        ) : (
          <p className="text-3xl font-bold leading-tight text-ink-800">{value}</p>
        )}
        <Link
          to={to}
          className={
            'inline-flex items-center gap-1 rounded text-sm font-medium text-brand-800 underline ' +
            'underline-offset-2 hover:text-brand-900 focus:outline-none focus-visible:ring-2 ' +
            'focus-visible:ring-focus'
          }
        >
          {linkLabel}
          <ArrowRightIcon className="h-3.5 w-3.5" />
        </Link>
      </div>
    </Card>
  );
}

interface PlatformIndicatorsProps {
  isAdmin: boolean;
  organizations: OrganizationSummary[];
  organizationsLoading: boolean;
  organizationsError: string | null;
}

export function PlatformIndicators(props: PlatformIndicatorsProps) {
  const { projects, isLoading: projectsLoading, error: projectsError } = useActiveProject();

  const projectsCard = (
    <Indicator
      icon={<FolderIcon className="h-6 w-6" />}
      label="Proyectos"
      value={projects.length}
      loading={projectsLoading}
      error={projectsError}
      to="/projects"
      linkLabel="Ver todos los proyectos"
    />
  );

  if (!props.isAdmin) {
    return <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">{projectsCard}</div>;
  }

  return <AdminIndicators {...props}>{projectsCard}</AdminIndicators>;
}

function AdminIndicators({
  organizations,
  organizationsLoading,
  organizationsError,
  children,
}: PlatformIndicatorsProps & { children: ReactNode }) {
  const { users, loading: usersLoading, error: usersError } = useUsers();

  const activeUsers = useMemo(() => users.filter((user) => user.active).length, [users]);
  const activeOrganizations = useMemo(
    () => organizations.filter((organization) => organization.active).length,
    [organizations],
  );

  return (
    <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <Indicator
        icon={<UsersIcon className="h-6 w-6" />}
        label="Usuarios"
        value={activeUsers}
        loading={usersLoading}
        error={usersError}
        to="/admin/users"
        linkLabel="Gestionar usuarios"
      />
      <Indicator
        icon={<BuildingIcon className="h-6 w-6" />}
        label="Organizaciones"
        value={activeOrganizations}
        loading={organizationsLoading}
        error={organizationsError}
        to="/admin/organizations"
        linkLabel="Ver organizaciones"
      />
      {children}
    </div>
  );
}
