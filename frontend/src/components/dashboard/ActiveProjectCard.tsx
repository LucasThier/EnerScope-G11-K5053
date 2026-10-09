import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { PlusIcon } from '../ui/icons';
import { Spinner } from '../ui/Spinner';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useAuth } from '../../hooks/useAuth';
import { formatDate } from '../../utils/date';

interface ActiveProjectCardProps {
  onNewProject: () => void;
}

/**
 * A platform admin's `GET /projects` returns every project on the platform, so
 * the "active" one resolved by `useActiveProject` may not be one they actually
 * belong to. This card checks that for admins only — a regular user's project
 * list is already filtered by membership on the backend, so no extra call is
 * needed for them.
 */
export function ActiveProjectCard({ onNewProject }: ActiveProjectCardProps) {
  const { user } = useAuth();
  const { activeProject, isLoading, error, selectProject } = useActiveProject();
  const navigate = useNavigate();
  const isAdmin = user?.platformRole === 'ADMIN';

  const [checkingMembership, setCheckingMembership] = useState(isAdmin);
  const [isMember, setIsMember] = useState(!isAdmin);
  const [membershipError, setMembershipError] = useState<string | null>(null);

  useEffect(() => {
    if (!isAdmin || !activeProject || !user) {
      setCheckingMembership(false);
      return;
    }
    let cancelled = false;
    setCheckingMembership(true);
    setMembershipError(null);
    projectsApi
      .members(activeProject.id)
      .then((res) => {
        if (cancelled) {
          return;
        }
        const members = res.data.data ?? [];
        setIsMember(members.some((member) => member.userId === user.id));
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        setMembershipError(getErrorMessage(err, 'No se pudo verificar tu acceso al proyecto'));
        setIsMember(false);
      })
      .finally(() => {
        if (!cancelled) {
          setCheckingMembership(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [isAdmin, activeProject, user]);

  function openInProjects() {
    if (!activeProject) {
      return;
    }
    selectProject(activeProject.id);
    navigate('/projects');
  }

  if (isLoading || checkingMembership) {
    return (
      <Card>
        <div className="flex items-center justify-center py-8">
          <Spinner />
        </div>
      </Card>
    );
  }

  if (error) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Proyecto activo</h2>
        <Alert tone="error" className="mt-4">
          {error}
        </Alert>
      </Card>
    );
  }

  if (!activeProject || (isAdmin && !isMember)) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Proyecto activo</h2>
        {membershipError && (
          <Alert tone="error" className="mt-3">
            {membershipError}
          </Alert>
        )}
        <p className="mt-2 text-sm text-ink-500">
          {isAdmin
            ? 'No formás parte de ningún proyecto todavía.'
            : 'Todavía no tenés proyectos.'}
        </p>
        <Button className="mt-4" onClick={onNewProject}>
          <PlusIcon className="h-4 w-4" />
          Nuevo proyecto
        </Button>
      </Card>
    );
  }

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink-800">Proyecto activo</h2>
      <dl className="mt-4 grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Nombre">{activeProject.name}</Field>
        <Field label="Organización">{activeProject.organizationName}</Field>
        <Field label="Integrantes">{activeProject.memberCount}</Field>
        <Field label="Última modificación">{formatDate(activeProject.lastModified)}</Field>
      </dl>
      <Button className="mt-4" onClick={openInProjects}>
        Ver en Proyectos
      </Button>
    </Card>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-1 text-sm text-ink-700">{children}</dd>
    </div>
  );
}
