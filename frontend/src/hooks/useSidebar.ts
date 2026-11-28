import { createContext, useContext, type RefObject } from 'react';

export interface SidebarContextValue {
  /** On screen: always when fixed (>= 80rem), only while open as a drawer. */
  isVisible: boolean;
  /** Fixed: takes its 240px out of the content instead of covering it. */
  pushes: boolean;
  isDrawer: boolean;
  isFixed: boolean;
  menuId: string;
  menuButtonRef: RefObject<HTMLButtonElement | null>;
  panelRef: RefObject<HTMLElement | null>;
  /** Opens or closes the drawer; does nothing while the sidebar is fixed. */
  toggle: () => void;
  close: () => void;
}

export const SidebarContext = createContext<SidebarContextValue | undefined>(undefined);

export function useSidebar(): SidebarContextValue {
  const ctx = useContext(SidebarContext);
  if (!ctx) {
    throw new Error('useSidebar must be used within a SidebarProvider');
  }
  return ctx;
}
