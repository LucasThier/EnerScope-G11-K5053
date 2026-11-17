import {
  PLATFORM_ADMIN_LABEL,
  PROJECT_ROLE_ACCESS_LABELS,
  verticalLabel,
} from '../components/projects/versionRoleLabels';
import { buildVersionTree, type VersionTreeNode } from './versionTree';
import type { VersionSummary, Vertical } from '../types/diagram';
import type { ProjectMember, ProjectMemberType } from '../types/project';
import type {
  AddVersionMemberRoleRequest,
  ProjectAccess,
  VersionAccess,
  VersionMember,
  VersionMemberRole,
  VersionRoleGrant,
  VersionRoleType,
} from '../types/versionAccess';

export const VERTICAL_ORDER: Vertical[] = [
  'EXTRACTION',
  'TRANSPORTATION',
  'LIQUEFACTION',
  'EXPORT',
  'INTERNAL_CONSUMPTION',
];

const ROLE_ORDER: Record<VersionRoleType, number> = { ADMIN: 0, EDITOR: 1, SIMULATOR: 2 };

const EPOCH = '1970-01-01T00:00:00.000Z';

function verticalIndex(vertical: Vertical | null): number {
  return vertical === null ? -1 : VERTICAL_ORDER.indexOf(vertical);
}

function compareRoles(
  aType: VersionRoleType,
  aVertical: Vertical | null,
  bType: VersionRoleType,
  bVertical: Vertical | null,
): number {
  if (aType !== bType) {
    return ROLE_ORDER[aType] - ROLE_ORDER[bType];
  }
  return verticalIndex(aVertical) - verticalIndex(bVertical);
}

export function sortGrants(grants: VersionRoleGrant[]): VersionRoleGrant[] {
  return [...grants].sort((a, b) => compareRoles(a.type, a.vertical, b.type, b.vertical));
}

export function sortMemberRoles(roles: VersionMemberRole[]): VersionMemberRole[] {
  return [...roles].sort((a, b) =>
    compareRoles(a.memberType, a.vertical, b.memberType, b.vertical),
  );
}

export function fullName(person: { firstName: string; lastName: string }): string {
  return `${person.firstName} ${person.lastName}`.trim();
}

export function sortVersionMembers(members: VersionMember[]): VersionMember[] {
  return [...members].sort((a, b) => fullName(a).localeCompare(fullName(b), 'es'));
}

export function canManageVersionRoles(entry: VersionAccess | null | undefined): boolean {
  return entry?.canManageRoles === true;
}

export function administersEveryVersion(entry: VersionAccess): boolean {
  return entry.source === 'PLATFORM_ADMIN' || entry.source === 'PROJECT_ADMIN';
}

export interface AccessSummary {
  administersAll: boolean;
  platformAdmin: boolean;
  projectRole: ProjectMemberType | null;
  explicit: VersionAccess[];
  withoutEffect: VersionAccess[];
  fallbackCount: number;
}

export function summarizeAccess(access: ProjectAccess): AccessSummary {
  return {
    administersAll: access.canManageProject,
    platformAdmin: access.platformAdmin,
    projectRole: access.projectRole,
    explicit: access.versions.filter((version) => version.source === 'VERSION_ROLES'),
    withoutEffect: access.versions.filter(
      (version) => version.source === 'PROJECT_ADMIN' && version.roles.length > 0,
    ),
    fallbackCount: access.versions.filter((version) => version.source === 'PROJECT_FALLBACK')
      .length,
  };
}

export function callerProjectRoleLabel(
  role: ProjectMemberType | null | undefined,
  platformAdmin: boolean,
): string | null {
  if (role) {
    return PROJECT_ROLE_ACCESS_LABELS[role];
  }
  return platformAdmin ? PLATFORM_ADMIN_LABEL : null;
}

export function capabilityLabels(entry: VersionAccess): string[] {
  const labels: string[] = [];
  if (entry.canView) {
    labels.push('Ver');
  }
  if (entry.canEdit) {
    if (entry.editableVerticals.length >= VERTICAL_ORDER.length) {
      labels.push('Editar todas las verticales');
    } else if (entry.editableVerticals.length > 0) {
      labels.push(`Editar (${entry.editableVerticals.map(verticalLabel).join(', ')})`);
    } else {
      labels.push('Editar');
    }
  }
  if (entry.canEditConnections) {
    labels.push('Editar conexiones');
  }
  if (entry.canSimulate) {
    labels.push('Simular');
  }
  if (entry.canManage) {
    labels.push('Administrar');
  }
  return labels;
}

export function versionBadge(entry: VersionAccess): string | null {
  if (entry.canManage) {
    return 'Administrás';
  }
  return entry.source === 'VERSION_ROLES' ? 'Con roles' : null;
}

export function assignableMembers(members: ProjectMember[]): ProjectMember[] {
  return members
    .filter((member) => member.active && member.memberType !== 'ADMIN' && member.memberType !== 'EXECUTIVE')
    .sort((a, b) => fullName(a).localeCompare(fullName(b), 'es'));
}

export interface RoleChoices {
  admin: boolean;
  simulator: boolean;
  editorVerticals: Vertical[];
}

export function availableChoices(held: VersionMemberRole[]): RoleChoices {
  return {
    admin: !held.some((role) => role.memberType === 'ADMIN'),
    simulator: !held.some((role) => role.memberType === 'SIMULATOR'),
    editorVerticals: VERTICAL_ORDER.filter(
      (vertical) =>
        !held.some((role) => role.memberType === 'EDITOR' && role.vertical === vertical),
    ),
  };
}

export function availableRoleTypes(choices: RoleChoices): VersionRoleType[] {
  const types: VersionRoleType[] = [];
  if (choices.admin) {
    types.push('ADMIN');
  }
  if (choices.editorVerticals.length > 0) {
    types.push('EDITOR');
  }
  if (choices.simulator) {
    types.push('SIMULATOR');
  }
  return types;
}

export function grantRequests(
  userId: string,
  type: VersionRoleType,
  verticals: Vertical[],
): AddVersionMemberRoleRequest[] {
  if (type === 'EDITOR') {
    return VERTICAL_ORDER.filter((vertical) => verticals.includes(vertical)).map((vertical) => ({
      userId,
      memberType: type,
      vertical,
    }));
  }
  return [{ userId, memberType: type }];
}

export function isLastRole(member: VersionMember): boolean {
  return member.roles.length <= 1;
}

export interface VersionRow {
  id: string;
  name: string;
  depth: number;
  access: VersionAccess;
}

function normalize(value: string): string {
  return value
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .trim();
}

export function versionSummaries(
  access: ProjectAccess,
  summaries: VersionSummary[],
): VersionSummary[] {
  const known = new Map(summaries.map((summary) => [summary.id, summary]));
  return access.versions.map((version) => {
    const summary = known.get(version.versionId);
    return {
      id: version.versionId,
      name: version.versionName,
      parentVersionId: summary?.parentVersionId ?? null,
      createdAt: summary?.createdAt ?? EPOCH,
    };
  });
}

export function versionRows(
  access: ProjectAccess,
  summaries: VersionSummary[],
  query: string,
  onlyManageable: boolean,
): VersionRow[] {
  const accessById = new Map(access.versions.map((version) => [version.versionId, version]));
  const rows: VersionRow[] = [];
  const walk = (nodes: VersionTreeNode[], depth: number) => {
    for (const node of nodes) {
      const entry = accessById.get(node.version.id);
      if (entry) {
        rows.push({ id: entry.versionId, name: entry.versionName, depth, access: entry });
      }
      walk(node.children, depth + 1);
    }
  };
  walk(buildVersionTree(versionSummaries(access, summaries)), 0);

  const needle = normalize(query);
  if (needle === '' && !onlyManageable) {
    return rows;
  }
  return rows
    .filter((row) => (needle === '' || normalize(row.name).includes(needle)))
    .filter((row) => !onlyManageable || row.access.canManageRoles)
    .map((row) => ({ ...row, depth: 0 }));
}

export function defaultVersionId(rows: VersionRow[], requested: string | null): string | null {
  if (requested && rows.some((row) => row.id === requested)) {
    return requested;
  }
  return rows.find((row) => row.access.canManageRoles)?.id ?? rows[0]?.id ?? null;
}
