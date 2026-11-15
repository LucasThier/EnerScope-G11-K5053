import { useState } from 'react';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { SearchField } from '../ui/SearchField';
import { Spinner } from '../ui/Spinner';
import { TrashIcon } from '../ui/icons';
import { versionAccessApi } from '../../api/versionAccess';
import { useVersionMembers } from '../../hooks/useVersionMembers';
import {
  ALL_VERSIONS_ADMIN_LABEL,
  PROJECT_ROLE_ACCESS_LABELS,
  versionRoleLabel,
} from '../projects/versionRoleLabels';
import { INACTIVE_ACCOUNT_LABEL, INACTIVE_ACCOUNT_TITLE } from '../projects/projectMemberTypeLabels';
import { AssignVersionRoleForm } from './AssignVersionRoleForm';
import { RemoveVersionRoleDialog, type RoleRemovalTarget } from './RemoveVersionRoleDialog';
import {
  assignableMembers,
  canManageVersionRoles,
  capabilityLabels,
  fullName,
  sortGrants,
  sortMemberRoles,
  sortVersionMembers,
} from '../../utils/versionRoles';
import type { ProjectMember } from '../../types/project';
import type { VersionAccess } from '../../types/versionAccess';

interface VersionRolesPanelProps {
  version: VersionAccess;
  projectMembers: ProjectMember[];
  callerUserId: string | null;
  onChanged: () => Promise<void>;
}

const chip = 'rounded-full bg-ink-100 px-2 py-0.5 text-xs font-semibold text-ink-700';
const MEMBER_SEARCH_THRESHOLD = 8;

function ownershipNote(version: VersionAccess): string | null {
  if (version.source === 'PLATFORM_ADMIN') {
    return 'Administrás esta versión como administrador de plataforma.';
  }
  if (version.source === 'PROJECT_ADMIN') {
    return 'Administrás esta versión como Administrador del proyecto.';
  }
  if (version.canManage) {
    return 'Administrás esta versión por tu rol de Administrador de versión.';
  }
  return null;
}

export function VersionRolesPanel({
  version,
  projectMembers,
  callerUserId,
  onChanged,
}: VersionRolesPanelProps) {
  const manageable = canManageVersionRoles(version);
  const { members, loading, error, reload } = useVersionMembers(manageable ? version.versionId : null);
  const [removing, setRemoving] = useState<RoleRemovalTarget | null>(null);
  const [filter, setFilter] = useState('');

  const projectMemberOf = (userId: string) =>
    projectMembers.find((candidate) => candidate.userId === userId);

  async function handleRemove(target: RoleRemovalTarget) {
    await versionAccessApi.removeRole(version.versionId, target.member.id, target.role.roleId);
    setRemoving(null);
    await Promise.all([reload(), onChanged()]);
  }

  async function handleGone() {
    setRemoving(null);
    await Promise.all([reload(), onChanged()]);
  }

  if (!manageable) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">{version.versionName}</h2>
        {version.canManage && ownershipNote(version) && (
          <p className="mt-1 text-sm text-ink-500">{ownershipNote(version)}</p>
        )}
        <p className="mt-1 text-sm text-ink-500">
          Solo los Administradores del proyecto asignan, cambian o quitan roles de versión y ven los
          de las demás personas.
        </p>
        <div className="mt-4 flex flex-col gap-3">
          <h3 className="text-base font-semibold text-ink-800">Tus accesos en esta versión</h3>
          {version.roles.length > 0 ? (
            <div className="flex flex-wrap gap-1">
              {sortGrants(version.roles).map((role) => (
                <span key={`${role.type}-${role.vertical}`} className={chip}>
                  {versionRoleLabel(role.type, role.vertical)}
                </span>
              ))}
            </div>
          ) : (
            <p className="text-sm text-ink-500">
              No tenés roles propios en esta versión: valen los permisos de tu rol en el proyecto
              {version.effectiveRole === 'ADMIN' || version.effectiveRole === 'EDITOR'
                ? ` (${PROJECT_ROLE_ACCESS_LABELS[version.effectiveRole]})`
                : ''}
              .
            </p>
          )}
          <p className="text-sm text-ink-700">{capabilityLabels(version).join(' · ')}</p>
        </div>
      </Card>
    );
  }

  const needle = filter.trim().toLowerCase();
  const rows = sortVersionMembers(members).filter(
    (member) =>
      needle === '' ||
      fullName(member).toLowerCase().includes(needle) ||
      member.userMail.toLowerCase().includes(needle),
  );
  const note = ownershipNote(version);
  const removingMember = removing ? projectMemberOf(removing.member.userId) : undefined;

  return (
    <Card>
      <h2 className="text-lg font-semibold text-ink-800">{version.versionName}</h2>
      {note && <p className="mt-1 text-sm text-ink-500">{note}</p>}

      <div className="mt-4 flex flex-col gap-4">
        <h3 className="text-base font-semibold text-ink-800">Roles en esta versión</h3>

        {loading ? (
          <div className="flex items-center gap-2 text-sm text-ink-500" role="status">
            <Spinner className="h-4 w-4" /> Cargando roles…
          </div>
        ) : error ? (
          <div className="flex flex-col items-start gap-3">
            <Alert tone="error" className="w-full">
              {error}
            </Alert>
            <Button type="button" variant="ghost" size="compact" onClick={() => void reload()}>
              Reintentar
            </Button>
          </div>
        ) : members.length === 0 ? (
          <Alert tone="info">
            Nadie tiene roles propios en esta versión: todas las personas usan los permisos de su rol
            en el proyecto.
          </Alert>
        ) : (
          <>
            {members.length > MEMBER_SEARCH_THRESHOLD && (
              <SearchField
                label="Buscar persona"
                placeholder="Buscar persona"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
              />
            )}
            <div className="overflow-x-auto rounded-control border border-ink-100">
              <table className="w-full border-collapse">
                <thead>
                  <tr className="border-b border-ink-100 bg-ink-50">
                    <th
                      scope="col"
                      className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500"
                    >
                      Persona
                    </th>
                    <th
                      scope="col"
                      className="px-3 py-2 text-left text-xs font-medium uppercase tracking-wide text-ink-500"
                    >
                      Roles
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((member) => {
                    const projectAdmin = projectMemberOf(member.userId)?.memberType === 'ADMIN';
                    return (
                      <tr key={member.id} className="border-b border-ink-100 last:border-b-0">
                        <td className="px-3 py-2 align-top text-sm">
                          <div className="font-semibold text-ink-800">{fullName(member)}</div>
                          <div className="text-ink-500">{member.userMail}</div>
                          <div className="mt-1 flex flex-wrap gap-1">
                            {!member.active && (
                              <span className={chip} title={INACTIVE_ACCOUNT_TITLE}>
                                {INACTIVE_ACCOUNT_LABEL}
                              </span>
                            )}
                            {projectAdmin && (
                              <span
                                className={chip}
                                title="Sus roles de versión no tienen efecto."
                              >
                                {ALL_VERSIONS_ADMIN_LABEL}
                              </span>
                            )}
                          </div>
                        </td>
                        <td className="px-3 py-2 align-top">
                          <ul className="flex flex-col gap-1">
                            {sortMemberRoles(member.roles).map((role) => {
                              const label = versionRoleLabel(role.memberType, role.vertical);
                              return (
                                <li
                                  key={role.roleId}
                                  className={`flex items-center gap-2 text-sm ${projectAdmin ? 'opacity-60' : ''}`}
                                >
                                  <span className={chip}>{label}</span>
                                  <button
                                    type="button"
                                    onClick={() => setRemoving({ member, role })}
                                    aria-label={`Quitar el rol ${label} de ${fullName(member)}`}
                                    title="Quitar rol"
                                    className="rounded-control p-1 text-ink-500 hover:bg-ink-50 hover:text-danger-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus"
                                  >
                                    <TrashIcon className="h-4 w-4" />
                                  </button>
                                </li>
                              );
                            })}
                          </ul>
                        </td>
                      </tr>
                    );
                  })}
                  {rows.length === 0 && (
                    <tr>
                      <td colSpan={2} className="px-3 py-3 text-sm text-ink-500">
                        Ninguna persona coincide con la búsqueda.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </>
        )}

        {!loading && !error && (
          <div className="border-t border-ink-100 pt-4">
            <AssignVersionRoleForm
              key={version.versionId}
              versionId={version.versionId}
              candidates={assignableMembers(projectMembers)}
              members={members}
              onAssigned={async () => {
                await Promise.all([reload(), onChanged()]);
              }}
            />
          </div>
        )}
      </div>

      <RemoveVersionRoleDialog
        target={removing}
        versionName={version.versionName}
        fallbackRoleLabel={removingMember ? PROJECT_ROLE_ACCESS_LABELS[removingMember.memberType] : null}
        administersAll={removingMember?.memberType === 'ADMIN'}
        losesOwnAdmin={
          removing !== null &&
          callerUserId !== null &&
          removing.member.userId === callerUserId &&
          removing.role.memberType === 'ADMIN' &&
          version.source === 'VERSION_ROLES'
        }
        onClose={() => setRemoving(null)}
        onConfirm={handleRemove}
        onGone={handleGone}
      />
    </Card>
  );
}
