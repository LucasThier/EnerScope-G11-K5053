import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { PageLoader } from '../components/ui/PageLoader';
import { MembersTable } from '../components/members/MembersTable';
import { AddProjectMemberCard } from '../components/projects/AddProjectMemberCard';
import { ProjectRoleChangeConfirmation } from '../components/projects/ProjectRoleChangeConfirmation';
import { RemoveProjectMemberDialog } from '../components/projects/RemoveProjectMemberDialog';
import {
  INACTIVE_ACCOUNT_LABEL,
  INACTIVE_ACCOUNT_TITLE,
  PROJECT_MEMBER_TYPE_LABELS,
} from '../components/projects/projectMemberTypeLabels';
import { projectsApi } from '../api/projects';
import { getErrorMessage, getErrorStatus } from '../api/errors';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import { useMemberRoleChange } from '../hooks/useMemberRoleChange';
import type { ProjectMember, ProjectMemberType } from '../types/project';

const countsAsAdmin = (member: ProjectMember) => member.memberType === 'ADMIN' && member.active;

export function ProjectTeamPage() {
  const { projectId } = useParams();
  const navigate = useNavigate();
  const { user: caller } = useAuth();
  const { projects, reload: reloadProjects } = useActiveProject();
  const [members, setMembers] = useState<ProjectMember[]>([]);
  const [loadedFor, setLoadedFor] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [removing, setRemoving] = useState<ProjectMember | null>(null);
  const [removedCount, setRemovedCount] = useState(0);

  const project = projects.find((candidate) => candidate.id === projectId);
  const projectName = project?.name ?? 'el proyecto';
  const isPlatformAdmin = caller?.platformRole === 'ADMIN';

  const reloadMembers = useCallback(async () => {
    if (!projectId) {
      return;
    }
    setError(null);
    try {
      const res = await projectsApi.members(projectId);
      setMembers(res.data.data ?? []);
    } catch (err) {
      if (getErrorStatus(err) === 403) {
        setForbidden(true);
      } else {
        setError(getErrorMessage(err, 'No se pudieron cargar los integrantes'));
      }
    } finally {
      setLoadedFor(projectId);
    }
  }, [projectId]);

  useEffect(() => {
    void reloadMembers();
  }, [reloadMembers]);

  const leave = useCallback(async () => {
    await reloadProjects();
    navigate('/projects', { replace: true });
  }, [navigate, reloadProjects]);

  async function handleChanged() {
    await reloadMembers();
    await reloadProjects();
  }

  async function handleRemoved(removedSelf: boolean) {
    if (removedSelf) {
      await leave();
      return;
    }
    setRemovedCount((count) => count + 1);
    await handleChanged();
  }

  const updateRole = useCallback(
    async (member: ProjectMember, memberType: ProjectMemberType) => {
      const res = await projectsApi.changeMemberRole(projectId ?? '', member.id, { memberType });
      return res.data.data ?? { ...member, memberType };
    },
    [projectId],
  );

  const handleRoleUpdated = useCallback((updated: ProjectMember) => {
    setMembers((current) =>
      current.map((member) => (member.id === updated.id ? updated : member)),
    );
  }, []);

  const handleSelfDemoted = useCallback(async () => {
    if (!isPlatformAdmin) {
      await leave();
    }
  }, [isPlatformAdmin, leave]);

  const roleChange = useMemberRoleChange({
    members,
    adminRole: 'ADMIN',
    countsAsAdmin,
    updateRole,
    onUpdated: handleRoleUpdated,
    onSelfDemoted: handleSelfDemoted,
  });

  if (!projectId) {
    return null;
  }
  if (forbidden) {
    return <Navigate to="/projects" replace />;
  }
  if (loadedFor !== projectId) {
    return <PageLoader />;
  }

  const callerMembership = members.find((member) => member.userId === caller?.id);
  const canManage = isPlatformAdmin || callerMembership?.memberType === 'ADMIN';
  if (!error && !canManage) {
    return <Navigate to="/projects" replace />;
  }

  return (
    <div>
      <header className="mb-6">
        <Link to="/projects" className="text-sm font-semibold text-brand-800 hover:underline">
          ← Proyectos
        </Link>
        <h1 className="mt-2 text-2xl font-semibold text-ink-800">
          {project?.name ?? 'Integrantes del proyecto'}
        </h1>
        <p className="mt-1 text-sm text-ink-500">
          Quiénes integran el proyecto y con qué rol. Los administradores gestionan el equipo y
          editan el proyecto; los editores trabajan sobre sus versiones.
        </p>
      </header>

      <Card padded={false} className="overflow-hidden">
        {error ? (
          <div className="px-4 py-6">
            <Alert tone="error">{error}</Alert>
          </div>
        ) : members.length === 0 ? (
          <div className="px-4 py-6">
            <Alert tone="info">Este proyecto todavía no tiene integrantes.</Alert>
          </div>
        ) : (
          <MembersTable
            members={members}
            roleLabels={PROJECT_MEMBER_TYPE_LABELS}
            inactiveLabel={INACTIVE_ACCOUNT_LABEL}
            inactiveTitle={INACTIVE_ACCOUNT_TITLE}
            removeLabel={(fullName) => `Quitar a ${fullName} del proyecto`}
            changeRoleLabel={(fullName) => `Cambiar el rol de ${fullName} en el proyecto`}
            onRemove={setRemoving}
            onChangeRole={roleChange.requestChange}
            busyMemberId={roleChange.busyMemberId}
            roleError={roleChange.pending ? null : roleChange.error}
          />
        )}
      </Card>

      {!error && (
        <AddProjectMemberCard
          key={`${projectId}-${removedCount}`}
          projectId={projectId}
          onAdded={handleChanged}
        />
      )}

      <Modal open={roleChange.pending !== null} onClose={roleChange.cancel} title="Cambiar el rol">
        {roleChange.pending && (
          <ProjectRoleChangeConfirmation
            change={roleChange.pending}
            projectName={projectName}
            error={roleChange.error}
            submitting={roleChange.busyMemberId !== null}
            onCancel={roleChange.cancel}
            onConfirm={() => void roleChange.confirm()}
          />
        )}
      </Modal>

      <RemoveProjectMemberDialog
        projectId={projectId}
        projectName={projectName}
        member={removing}
        onClose={() => setRemoving(null)}
        onRemoved={handleRemoved}
      />
    </div>
  );
}
