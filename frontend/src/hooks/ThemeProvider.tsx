import { useEffect, useMemo, useState, type ReactNode } from 'react';
import { useLocalPreference } from './useLocalPreference';
import {
  THEME_PREFERENCES,
  ThemeContext,
  type ResolvedTheme,
  type ThemeContextValue,
  type ThemePreference,
} from './useTheme';

const THEME_KEY = 'theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

function readSystem(): ResolvedTheme {
  try {
    return window.matchMedia(DARK_QUERY).matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [stored, setStored] = useLocalPreference<ThemePreference>(THEME_KEY, 'light');
  const [system, setSystem] = useState<ResolvedTheme>(readSystem);

  const preference = THEME_PREFERENCES.includes(stored) ? stored : 'light';
  const resolved: ResolvedTheme = preference === 'system' ? system : preference;

  useEffect(() => {
    let query: MediaQueryList;
    try {
      query = window.matchMedia(DARK_QUERY);
    } catch {
      return;
    }
    const onChange = () => setSystem(query.matches ? 'dark' : 'light');
    onChange();
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    document.documentElement.setAttribute('data-theme', resolved);
  }, [resolved]);

  const value = useMemo<ThemeContextValue>(
    () => ({ preference, resolved, system, setPreference: setStored }),
    [preference, resolved, system, setStored],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}
