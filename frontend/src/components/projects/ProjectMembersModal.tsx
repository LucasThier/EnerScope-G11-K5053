import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Modal } from '../ui/Modal';
import { MembersTable } from '../members/MembersTable';
import {
  INACTIVE_ACCOUNT_LABEL,
  INACTIVE_ACCOUNT_TITLE,
  PROJECT_MEMBER_TYPE_LABELS,
} from './projectMemberTypeLabels';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { useAuth } from '../../hooks/useAuth';
import { formatDate } from '../../utils/date';
import type { ProjectMember, ProjectSummary } from '../../types/project';

interface ProjectMembersModalProps {
  project: ProjectSummary | null;
  onClose: () => void;
}

export function ProjectMembersModal({ project, onClose }: ProjectMembersModalProps) {
  const { user: caller } = useAuth();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const projectId = project?.id ?? null;

  useEffect(() => {
    if (!projectId) {
      setMembers([]);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    projectsApi
      .members(projectId)
      .then((res) => {
        if (!cancelled) {
          setMembers(res.data.data ?? []);
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [projectId]);

  const callerMembership = members.find((member) => member.userId === caller?.id);
  const canManage =
    !loading &&
    (caller?.platformRole === 'ADMIN' || callerMembership?.memberType === 'ADMIN');

  return (
    <Modal open={project !== null} onClose={onClose} title={project?.name ?? ''} size="lg">
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Organización">{project?.organizationName}</Field>
        <Field label="Actualizado">{formatDate(project?.lastModified)}</Field>
        <Field label="Descripción" className="sm:col-span-2">
          {project?.description}
        </Field>
      </dl>

      <div className="mt-6 flex flex-wrap items-baseline justify-between gap-2">
        <h3 className="text-sm font-semibold text-ink-800">
          Integrantes
          {!loading && !error && members.length > 0 && (
            <span className="ml-2 font-normal text-ink-500">{members.length}</span>
          )}
        </h3>
        {canManage && project && (
          <Link
            to={`/projects/${project.id}/team`}
            onClick={onClose}
            className="text-sm font-semibold text-brand-800 hover:underline"
          >
            Administrar integrantes
          </Link>
        )}
      </div>

      <div className="mt-3">
        {loading ? (
          <p className="py-6 text-center text-sm text-ink-500">Cargando integrantes…</p>
        ) : error ? (
          <Alert tone="error">{error}</Alert>
        ) : members.length === 0 ? (
          <p className="py-6 text-center text-sm text-ink-500">
            Este proyecto todavía no tiene integrantes.
          </p>
        ) : (
          <MembersTable
            members={members}
            roleLabels={PROJECT_MEMBER_TYPE_LABELS}
            inactiveLabel={INACTIVE_ACCOUNT_LABEL}
            inactiveTitle={INACTIVE_ACCOUNT_TITLE}
          />
        )}
      </div>
    </Modal>
  );
}

function Field({
  label,
  className = '',
  children,
}: {
  label: string;
  className?: string;
  children: ReactNode;
}) {
  return (
    <div className={className}>
      <dt className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-1 text-sm text-ink-700">{children}</dd>
    </div>
  );
}
