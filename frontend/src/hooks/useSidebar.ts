import { createContext, useContext, type RefObject } from 'react';

export type SidebarOrigin = 'route' | 'pointer' | 'hover' | 'keyboard';

export interface SidebarContextValue {
  origin: SidebarOrigin | null;
  isVisible: boolean;
  pushes: boolean;
  isDrawer: boolean;
  isPinned: boolean;
  isFixed: boolean;
  canPin: boolean;
  menuId: string;
  menuButtonRef: RefObject<HTMLButtonElement | null>;
  panelRef: RefObject<HTMLElement | null>;
  toggleFromButton: (viaKeyboard: boolean) => void;
  togglePin: (viaKeyboard: boolean) => void;
  close: () => void;
  keepOpen: () => void;
}

export const SidebarContext = createContext<SidebarContextValue | undefined>(undefined);

export function useSidebar(): SidebarContextValue {
  const ctx = useContext(SidebarContext);
  if (!ctx) {
    throw new Error('useSidebar must be used within a SidebarProvider');
  }
  return ctx;
}
