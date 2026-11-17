import { useCallback, useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Alert } from '../components/ui/Alert';
import { Card } from '../components/ui/Card';
import { Modal } from '../components/ui/Modal';
import { PageLoader } from '../components/ui/PageLoader';
import { TabPanel, Tabs } from '../components/ui/Tabs';
import { MembersTable } from '../components/members/MembersTable';
import { VersionRolesTab } from '../components/versionRoles/VersionRolesTab';
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
import { isPlatformAdmin as resolveIsPlatformAdmin } from '../permissions/access';
import { useMemberRoleChange } from '../hooks/useMemberRoleChange';
import { useProjectAccess } from '../hooks/useProjectAccess';
import { accessFailureMessage } from '../utils/versionAccessRules';
import { AccessFailureAlert } from '../components/versionRoles/AccessFailureAlert';
import { lastProjectAdminReason } from '../utils/membershipGuards';
import { callerProjectRoleLabel } from '../utils/versionRoles';
import type { ProjectMember, ProjectMemberType } from '../types/project';

const countsAsAdmin = (member: ProjectMember) => member.memberType === 'ADMIN' && member.active;

const TABS = [
  { id: 'integrantes', label: 'Integrantes' },
  { id: 'roles', label: 'Roles por versión' },
];
const TABS_PREFIX = 'project-team';

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
  const [searchParams, setSearchParams] = useSearchParams();
  const projectAccess = useProjectAccess(projectId, { withSummaries: true });
  const reloadAccess = projectAccess.reload;

  const activeTab = searchParams.get('tab') === 'roles' ? 'roles' : 'integrantes';

  const changeTab = useCallback(
    (tabId: string) => {
      setSearchParams(
        (previous) => {
          const next = new URLSearchParams(previous);
          if (tabId === 'roles') {
            next.set('tab', 'roles');
          } else {
            next.delete('tab');
            next.delete('version');
          }
          return next;
        },
        { replace: true },
      );
    },
    [setSearchParams],
  );

  const project = projects.find((candidate) => candidate.id === projectId);
  const projectName = project?.name ?? 'el proyecto';
  const isPlatformAdmin = resolveIsPlatformAdmin(caller);

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
    await reloadAccess();
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

  const handleRoleUpdated = useCallback(
    (updated: ProjectMember) => {
      setMembers((current) =>
        current.map((member) => (member.id === updated.id ? updated : member)),
      );
      void reloadAccess();
    },
    [reloadAccess],
  );

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
  const canManage = projectAccess.access?.canManageProject ?? false;
  const accessFailure = accessFailureMessage(projectAccess.error, false);
  const callerRoleLabel = callerProjectRoleLabel(
    projectAccess.access?.projectRole ?? callerMembership?.memberType,
    projectAccess.access?.platformAdmin ?? isPlatformAdmin,
  );

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
          Quiénes integran el proyecto y qué puede hacer cada persona en cada versión. Los
          Administradores del proyecto gestionan el equipo, asignan los roles de cada versión y
          administran todas las versiones.
        </p>
        {callerRoleLabel && (
          <p className="mt-1 text-sm text-ink-700">
            Tu rol en el proyecto:{' '}
            <span className="font-semibold text-ink-800">{callerRoleLabel}</span>.
          </p>
        )}
      </header>

      <AccessFailureAlert message={accessFailure} onRetry={() => void reloadAccess()} />

      <Tabs
        label="Equipo del proyecto"
        idPrefix={TABS_PREFIX}
        tabs={TABS}
        active={activeTab}
        onChange={changeTab}
      />

      <TabPanel idPrefix={TABS_PREFIX} id="integrantes" active={activeTab}>
        {!error && !canManage && !projectAccess.loading && !accessFailure && (
          <Alert tone="info" className="mb-4">
            Podés ver quiénes integran el proyecto. Solo los Administradores del proyecto pueden
            cambiar el equipo.
          </Alert>
        )}

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
              onRemove={canManage ? setRemoving : undefined}
              onChangeRole={canManage ? roleChange.requestChange : undefined}
              disabledReason={
                canManage ? (member) => lastProjectAdminReason(members, member) : undefined
              }
              busyMemberId={roleChange.busyMemberId}
              roleError={roleChange.pending ? null : roleChange.error}
            />
          )}
        </Card>

        {!error && canManage && (
          <AddProjectMemberCard
            key={`${projectId}-${removedCount}`}
            projectId={projectId}
            onAdded={handleChanged}
          />
        )}
      </TabPanel>

      <TabPanel idPrefix={TABS_PREFIX} id="roles" active={activeTab}>
        <VersionRolesTab
          projectId={projectId}
          access={projectAccess.access}
          summaries={projectAccess.summaries}
          loading={projectAccess.loading}
          error={projectAccess.error}
          projectMembers={members}
          callerUserId={caller?.id ?? null}
          reload={reloadAccess}
        />
      </TabPanel>

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
