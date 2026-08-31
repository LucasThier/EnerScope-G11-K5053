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
}

export interface OrganizationMemberSummary {
  id: string;
  userId: string;
  userMail: string;
  memberType: string;
  permissions: string[];
}

export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T | null;
  timestamp: string;
}
