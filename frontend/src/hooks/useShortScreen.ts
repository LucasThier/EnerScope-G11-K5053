import { useSyncExternalStore } from 'react';

/** 1280×720 and the like: a viewport 760px tall or less, where Inicio shows one scenario row less. */
export const SHORT_SCREEN_QUERY = '(max-height: 760px)';

function subscribe(onChange: () => void): () => void {
  const query = window.matchMedia(SHORT_SCREEN_QUERY);
  query.addEventListener('change', onChange);
  return () => query.removeEventListener('change', onChange);
}

export function useShortScreen(): boolean {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(SHORT_SCREEN_QUERY).matches,
    () => false,
  );
}
