import type { NewSessionResponse } from '../types/auth';

/**
 * Small wrapper around token storage. Centralising it here means the storage
 * strategy (LocalStorage today, cookies or memory tomorrow) can change in one
 * place without touching the rest of the app.
 */
const ACCESS_TOKEN_KEY = 'accessToken';
const REFRESH_TOKEN_KEY = 'refreshToken';

export const session = {
  getAccessToken: (): string | null => localStorage.getItem(ACCESS_TOKEN_KEY),

  getRefreshToken: (): string | null => localStorage.getItem(REFRESH_TOKEN_KEY),

  save: ({ accessToken, refreshToken }: NewSessionResponse): void => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken);
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken);
  },

  clear: (): void => {
    localStorage.removeItem(ACCESS_TOKEN_KEY);
    localStorage.removeItem(REFRESH_TOKEN_KEY);
  },

  isAuthenticated: (): boolean => localStorage.getItem(ACCESS_TOKEN_KEY) !== null,
};
