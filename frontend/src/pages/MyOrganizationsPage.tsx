import { useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Button } from '../components/ui/Button';
import { Card } from '../components/ui/Card';
import { BuildingIcon } from '../components/ui/icons';
import { Spinner } from '../components/ui/Spinner';
import { MEMBER_TYPE_LABELS } from '../components/organizations/memberTypeLabels';
import { useActiveProject } from '../hooks/useActiveProject';
import { useMyOrganizations } from '../hooks/useMyOrganizations';

export function MyOrganizationsPage() {
  const { memberships, loading, error, reload } = useMyOrganizations();
  const { projects, isLoading: projectsLoading, error: projectsError } = useActiveProject();
  const navigate = useNavigate();

  const projectCounts = useMemo(() => {
    const counts = new Map<string, number>();
    for (const project of projects) {
      counts.set(project.organizationId, (counts.get(project.organizationId) ?? 0) + 1);
    }
    return counts;
  }, [projects]);
  const showCounts = !projectsLoading && !projectsError;
  const isMemberOnly = memberships.some((membership) => membership.role === 'MEMBER');

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold text-ink-800">Mis organizaciones</h1>
        <p className="mt-1 text-sm text-ink-500">Las organizaciones a las que pertenecés.</p>
        {isMemberOnly && (
          <p className="mt-2 text-sm text-ink-500">
            Solo los propietarios pueden gestionar los integrantes de una organización.
          </p>
        )}
      </header>

      <Card padded={false}>
        {loading ? (
          <div className="flex items-center justify-center py-10">
            <Spinner />
          </div>
        ) : error ? (
          <div className="p-6">
            <Alert tone="error">{error}</Alert>
            <Button
              variant="secondary"
              size="compact"
              className="mt-4"
              onClick={() => void reload()}
            >
              Reintentar
            </Button>
          </div>
        ) : memberships.length === 0 ? (
          <p className="px-6 py-8 text-sm text-ink-500">
            Todavía no pertenecés a ninguna organización.
          </p>
        ) : (
          <ul>
            {memberships.map((membership) => {
              const isOwner = membership.role === 'OWNER';
              return (
                <li
                  key={membership.id}
                  aria-label={`${membership.name}, ${MEMBER_TYPE_LABELS[membership.role]}`}
                  className="flex flex-wrap items-center gap-x-4 gap-y-3 border-b border-ink-100 px-6 py-4 last:border-b-0"
                >
                  <span
                    className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-brand-50 text-brand-800"
                    aria-hidden="true"
                  >
                    <BuildingIcon className="h-5 w-5" />
                  </span>
                  <div className="min-w-0 flex-1 basis-48">
                    <p
                      className="break-words text-sm font-semibold text-ink-800"
                      title={membership.name}
                    >
                      {membership.name}
                    </p>
                    {showCounts && (
                      <p className="mt-0.5 text-xs text-ink-500">
                        {projectCountLabel(projectCounts.get(membership.id) ?? 0)}
                      </p>
                    )}
                  </div>
                  <span
                    className={
                      'inline-flex items-center rounded-full px-2 py-1 text-xs font-semibold ' +
                      (isOwner ? 'bg-brand-100 text-brand-900' : 'bg-ink-100 text-ink-600')
                    }
                  >
                    {MEMBER_TYPE_LABELS[membership.role]}
                  </span>
                  {isOwner && (
                    <Button
                      variant="secondary"
                      size="compact"
                      aria-label={`Gestionar integrantes de ${membership.name}`}
                      onClick={() => navigate(`/organizations/${membership.id}`)}
                    >
                      Gestionar integrantes
                    </Button>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </Card>
    </div>
  );
}

function projectCountLabel(count: number): string {
  if (count === 0) {
    return 'Sin proyectos';
  }
  return count === 1 ? '1 proyecto' : `${count} proyectos`;
}
