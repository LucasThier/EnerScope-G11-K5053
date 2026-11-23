/**
 * The one decision the signed-in sidebar makes about its layout, kept pure so it
 * can be tested without a browser.
 *
 * At 80rem (1280px) and wider the sidebar is a permanent column: fixed, full
 * height, carrying the logo, pushing the content. Below that it is a drawer
 * under the top bar, opened from the hamburger. There is no third mode: the
 * desktop overlay between 64rem and 80rem and the pin are gone (2026-11-23),
 * because a permanent 240px column at 1024px squeezed the Projects and Users
 * tables into a horizontal scroll and the Comparisons catalogue to ~360px.
 */
export const FIXED_QUERY = '(min-width: 80rem)';

export type SidebarMode = 'fixed' | 'drawer';

export function sidebarMode(wide: boolean): SidebarMode {
  return wide ? 'fixed' : 'drawer';
}

export interface SidebarLayout {
  isFixed: boolean;
  isDrawer: boolean;
  /** On screen: always when fixed, only while open as a drawer. */
  isVisible: boolean;
  /** Takes its own width out of the content instead of covering it. */
  pushes: boolean;
}

export function sidebarLayout(mode: SidebarMode, drawerOpen: boolean): SidebarLayout {
  const isFixed = mode === 'fixed';
  return {
    isFixed,
    isDrawer: !isFixed,
    isVisible: isFixed || drawerOpen,
    pushes: isFixed,
  };
}

/**
 * `sidebarPinned` stored the pin preference until 2026-11-23. Nothing reads it
 * any more; this removes it once per load so it does not linger. Safe to delete
 * this (and its call) once old builds are long gone. Never reuse the key name
 * for something with a different shape.
 */
export const RETIRED_PINNED_KEY = 'sidebarPinned';

export function forgetRetiredPreference(storage: Pick<Storage, 'removeItem'> | undefined): void {
  try {
    storage?.removeItem(RETIRED_PINNED_KEY);
  } catch {
    return;
  }
}
