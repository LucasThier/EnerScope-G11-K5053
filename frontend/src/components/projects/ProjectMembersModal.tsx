import { useEffect, useState, type ReactNode } from 'react';
import { Alert } from '../ui/Alert';
import { Modal } from '../ui/Modal';
import { projectsApi } from '../../api/projects';
import { getErrorMessage } from '../../api/errors';
import { formatDate } from '../../utils/date';
import type { ProjectMember, ProjectMemberType, ProjectSummary } from '../../types/project';

interface ProjectMembersModalProps {
  project: ProjectSummary | null;
  onClose: () => void;
}

const ROLE_LABELS: Record<ProjectMemberType, string> = {
  ADMIN: 'Administrador',
  EDITOR: 'Editor',
};

const headerCell = 'px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500';
const bodyCell = 'px-3 py-2 align-top text-sm text-ink-700';

export function ProjectMembersModal({ project, onClose }: ProjectMembersModalProps) {
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

  return (
    <Modal open={project !== null} onClose={onClose} title={project?.name ?? ''} size="lg">
      <dl className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <Field label="Organización">{project?.organizationName}</Field>
        <Field label="Actualizado">{formatDate(project?.lastModified)}</Field>
        <Field label="Descripción" className="sm:col-span-2">
          {project?.description}
        </Field>
      </dl>

      <h3 className="mt-6 text-sm font-semibold text-ink-800">
        Integrantes
        {!loading && !error && members.length > 0 && (
          <span className="ml-2 font-normal text-ink-500">{members.length}</span>
        )}
      </h3>

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
          <div className="overflow-x-auto">
            <table className="w-full border-collapse">
              <thead>
                <tr className="border-b border-ink-100">
                  <th scope="col" className={`${headerCell} whitespace-nowrap`}>
                    Nombre
                  </th>
                  <th scope="col" className={headerCell}>
                    Email
                  </th>
                  <th scope="col" className={headerCell}>
                    Puesto
                  </th>
                  <th scope="col" className={headerCell}>
                    Rol
                  </th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink-100">
                {members.map((member) => (
                  <tr key={member.id}>
                    <td className={`${bodyCell} font-semibold text-ink-800`}>
                      {member.firstName} {member.lastName}
                      {!member.active && (
                        <span className="ml-2 text-xs font-normal text-ink-500">Suspendido</span>
                      )}
                    </td>
                    <td className={bodyCell}>{member.userMail}</td>
                    <td className={bodyCell}>{member.jobTitle ?? '—'}</td>
                    <td className={`${bodyCell} whitespace-nowrap`}>
                      {ROLE_LABELS[member.memberType]}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
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
