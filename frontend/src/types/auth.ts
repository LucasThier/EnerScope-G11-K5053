import type { ProjectMemberType } from './project';

/** Platform-wide role for a user. Mirrors the backend PlatformRole enum. */
export type PlatformRole = 'ADMIN' | 'USER';

export interface LoginRequest {
  mail: string;
  password: string;
}

export interface RegisterRequest {
  mail: string;
  firstName: string;
  lastName: string;
  password: string;
  /** Optional; admins may create other admins. Defaults to USER on the server. */
  role?: PlatformRole;
}

/** Payload to register a brand new user directly into an organization. */
export interface RegisterOrganizationUserRequest {
  mail: string;
  firstName: string;
  lastName: string;
  password: string;
}

export interface RefreshRequest {
  refreshToken: string;
}

/** Public projection of the authenticated user. Mirrors backend UserSummaryDTO. */
export interface UserSummary {
  id: string;
  mail: string;
  firstName: string;
  lastName: string;
  platformRole: PlatformRole;
  /** Free-form job title; null for accounts created without one. */
  jobTitle: string | null;
}

export interface UserListItem {
  id: string;
  mail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  platformRole: PlatformRole;
  active: boolean;
  organizationCount: number;
}

export interface UpdateRoleRequest {
  platformRole: PlatformRole;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

export interface UpdateProfileRequest {
  firstName?: string;
  lastName?: string;
  jobTitle?: string;
}

export interface NewSessionResponse {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  user: UserSummary;
}

export interface OrganizationSummary {
  id: string;
  name: string;
  createdAt: string;
  active: boolean;
  memberCount: number;
}

export interface UpdateOrganizationRequest {
  name: string;
}

export interface UserSearchResult {
  id: string;
  firstName: string;
  lastName: string;
  mail: string;
}

export interface AddOrganizationMemberRequest {
  userId: string;
  memberType: OrganizationMemberType;
}

export interface UpdateOrganizationMemberRoleRequest {
  memberType: OrganizationMemberType;
}

export type OrganizationMemberType = 'OWNER' | 'MEMBER';

export type OrganizationMemberPermission = 'MANAGE_ORGANIZATION' | 'VIEW_ORGANIZATION';

export interface OrganizationMemberSummary {
  id: string;
  userId: string;
  userMail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  active: boolean;
  memberType: OrganizationMemberType;
  permissions: OrganizationMemberPermission[];
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
}

export interface UserOrganizationMembership {
  organizationId: string;
  organizationName: string;
  organizationActive: boolean;
  memberType: OrganizationMemberType;
}

export interface UserProjectMembership {
  projectId: string;
  projectName: string;
  organizationId: string;
  organizationName: string;
  organizationActive: boolean;
  memberType: ProjectMemberType;
}

/** Full read-only record behind `GET /users/{id}`. Mirrors backend UserDetailDTO. */
export interface UserDetail {
  id: string;
  mail: string;
  firstName: string;
  lastName: string;
  jobTitle: string | null;
  platformRole: PlatformRole;
  active: boolean;
  organizations: UserOrganizationMembership[];
  projects: UserProjectMembership[];
}
