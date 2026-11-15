import type { Vertical } from './diagram';
import type { ProjectMemberPermission, ProjectMemberType } from './project';

export type VersionRoleType = 'ADMIN' | 'EDITOR' | 'SIMULATOR';

export type VersionAccessSource =
  | 'PLATFORM_ADMIN'
  | 'PROJECT_ADMIN'
  | 'VERSION_ROLES'
  | 'PROJECT_FALLBACK';

export interface VersionRoleGrant {
  type: VersionRoleType;
  vertical: Vertical | null;
}

export interface VersionAccess {
  versionId: string;
  versionName: string;
  source: VersionAccessSource;
  roles: VersionRoleGrant[];
  effectiveRole: string | null;
  editableVerticals: Vertical[];
  canView: boolean;
  canEdit: boolean;
  canEditConnections: boolean;
  canSimulate: boolean;
  canManage: boolean;
  canManageRoles: boolean;
  canViewEconomicResults: boolean;
  canViewEconomicConfiguration: boolean;
  canEditEconomicConfiguration: boolean;
}

export interface ProjectAccess {
  projectId: string;
  platformAdmin: boolean;
  projectRole: ProjectMemberType | null;
  projectPermissions: ProjectMemberPermission[];
  canManageProject: boolean;
  canCreateVersions: boolean;
  versions: VersionAccess[];
}

export type VersionMemberPermission =
  | 'MANAGE_VERSION'
  | 'EDIT_VERSION'
  | 'SIMULATE_VERSION'
  | 'VIEW_VERSION';

export interface VersionMemberRole {
  roleId: string;
  memberType: VersionRoleType;
  vertical: Vertical | null;
  permissions: VersionMemberPermission[];
}

export interface VersionMember {
  id: string;
  userId: string;
  userMail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  active: boolean;
  roles: VersionMemberRole[];
}

export interface AddVersionMemberRoleRequest {
  userId: string;
  memberType: VersionRoleType;
  vertical?: Vertical | null;
}
