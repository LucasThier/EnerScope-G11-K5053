import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { authApi } from '../api/auth';
import { organizationsApi } from '../api/organizations';
import { session } from '../api/session';
import type {
  LoginRequest,
  RegisterRequest,
  RegisterOrganizationUserRequest,
  UserSummary,
  OrganizationMemberSummary,
} from '../types/auth';
import { AuthContext, type AuthContextValue } from './useAuth';

/**
 * Holds the authentication state for the app and exposes the login / register /
 * logout / refresh actions through {@link useAuth}. On mount it rehydrates the
 * session from the stored refresh token so a page reload keeps the user signed
 * in (and silently logs them out if the token is no longer valid).
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(() => session.getUser());
  const [isLoading, setIsLoading] = useState(true);

  const refresh = useCallback(async (): Promise<UserSummary | null> => {
    const refreshToken = session.getRefreshToken();
    if (!refreshToken) {
      setUser(null);
      return null;
    }
    try {
      const res = await authApi.refresh({ refreshToken });
      const data = res.data.data;
      if (data) {
        session.save(data);
        setUser(data.user);
        return data.user;
      }
      session.clear();
      setUser(null);
      return null;
    } catch {
      session.clear();
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    let active = true;
    (async () => {
      if (session.getRefreshToken()) {
        await refresh();
      }
      if (active) {
        setIsLoading(false);
      }
    })();
    return () => {
      active = false;
    };
  }, [refresh]);

  const login = useCallback(async (data: LoginRequest): Promise<UserSummary> => {
    const res = await authApi.login(data);
    const body = res.data.data;
    if (!body) {
      throw new Error(res.data.message || 'Login failed');
    }
    session.save(body);
    setUser(body.user);
    return body.user;
  }, []);

  const register = useCallback(async (data: RegisterRequest): Promise<UserSummary> => {
    const res = await authApi.register(data);
    const created = res.data.data;
    if (!created) {
      throw new Error(res.data.message || 'Registration failed');
    }
    return created;
  }, []);

  const registerInOrganization = useCallback(
    async (
      organizationId: string,
      data: RegisterOrganizationUserRequest,
    ): Promise<OrganizationMemberSummary> => {
      const res = await organizationsApi.registerUser(organizationId, data);
      const created = res.data.data;
      if (!created) {
        throw new Error(res.data.message || 'Registration failed');
      }
      return created;
    },
    [],
  );

  const logout = useCallback(async (): Promise<void> => {
    await authApi.logout();
    setUser(null);
  }, []);

  const value: AuthContextValue = {
    user,
    isAuthenticated: user !== null,
    isLoading,
    login,
    register,
    registerInOrganization,
    logout,
    refresh,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
