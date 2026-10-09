import { useCallback, useState } from 'react';

/**
 * A small piece of UI state that survives reloads (a collapsed sidebar, the last
 * project someone opened). Every access is guarded: LocalStorage throws in
 * Safari's private mode and can be disabled outright, and none of these
 * preferences are worth breaking a render over — they simply fall back.
 */
export function useLocalPreference<T>(key: string, fallback: T): [T, (value: T) => void] {
  const [value, setValue] = useState<T>(() => read(key, fallback));

  const store = useCallback(
    (next: T) => {
      setValue(next);
      write(key, next);
    },
    [key],
  );

  return [value, store];
}

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw === null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

function write(key: string, value: unknown): void {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch {
    return;
  }
}
