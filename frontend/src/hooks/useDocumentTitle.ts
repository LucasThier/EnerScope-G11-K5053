import { useEffect } from 'react';

export const APP_NAME = 'EnerScope';

export function documentTitleFor(section: string | null): string {
  return section ? `${section} · ${APP_NAME}` : APP_NAME;
}

export function useDocumentTitle(section: string | null): void {
  useEffect(() => {
    document.title = documentTitleFor(section);
  }, [section]);
}
