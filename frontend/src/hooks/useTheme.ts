import { createContext, useContext } from 'react';

export type ThemePreference = 'light' | 'dark' | 'system';

export type ResolvedTheme = 'light' | 'dark';

export const THEME_PREFERENCES: ThemePreference[] = ['light', 'dark', 'system'];

export interface ThemeContextValue {
  preference: ThemePreference;
  resolved: ResolvedTheme;
  system: ResolvedTheme;
  setPreference: (value: ThemePreference) => void;
}

export const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export function useTheme(): ThemeContextValue {
  const ctx = useContext(ThemeContext);
  if (!ctx) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return ctx;
}
