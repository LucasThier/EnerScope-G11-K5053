import type { NewSessionResponse, UserSummary } from '../types/auth';

/**
 * Small wrapper around token + current-user storage. Centralising it here means
 * the storage strategy (LocalStorage today, cookies or memory tomorrow) can
 * change in one place without touching the rest of the app.
 */
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';
const USER_KEY = 'authUser';

export const session = {
  getAccessToken: (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY),

  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),

  getUser: (): UserSummary | null => {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw) as UserSummary;
    } catch {
      return null;
    }
  },

  save: ({ accessToken, refreshToken, user }: NewSessionResponse): void => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  },

  clear: (): void => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  },

  isAuthenticated: (): boolean => localStorage.getItem(ACCESS_TOKEN_KEY) !== null,
};
