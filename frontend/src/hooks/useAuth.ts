import { createContext, useContext } from 'react';
import type {
  LoginRequest,
  RegisterRequest,
  RegisterOrganizationUserRequest,
  UserSummary,
  OrganizationMemberSummary,
} from '../types/auth';

export interface AuthContextValue {
  /** The authenticated user, or null when logged out. */
  user: UserSummary | null;
  isAuthenticated: boolean;
  /** True while the initial session bootstrap (token refresh) is in flight. */
  isLoading: boolean;
  login: (data: LoginRequest) => Promise<UserSummary>;
  /** Admin-only account creation. Does not change the current session. */
  register: (data: RegisterRequest) => Promise<UserSummary>;
  /** Register a new user straight into an organization (admin or org owner). */
  registerInOrganization: (
    organizationId: string,
    data: RegisterOrganizationUserRequest,
  ) => Promise<OrganizationMemberSummary>;
  logout: () => Promise<void>;
  refresh: () => Promise<UserSummary | null>;
}

export const AuthContext = createContext<AuthContextValue | undefined>(undefined);

/** Access the auth state and actions. Must be used within an {@link AuthProvider}. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
