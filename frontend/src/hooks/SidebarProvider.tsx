import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import {
  FIXED_QUERY,
  forgetRetiredPreference,
  sidebarLayout,
  sidebarMode,
} from './sidebarMode';
import { SidebarContext, type SidebarContextValue } from './useSidebar';

function readWide(): boolean {
  try {
    return window.matchMedia(FIXED_QUERY).matches;
  } catch {
    return true;
  }
}

function isTypingTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }
  const tag = target.tagName;
  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
}

function hasOpenDialog(): boolean {
  return document.querySelector('[role="dialog"]') !== null;
}

function hasOpenMenu(): boolean {
  return document.querySelector('[role="menu"]') !== null;
}

/**
 * Owns the sidebar's one media query (80rem) and, below it, whether the drawer
 * is open. See `sidebarMode.ts` for why there are only two modes.
 */
export function SidebarProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const [isWide, setIsWide] = useState(readWide);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const menuId = useId();

  const { isFixed, isDrawer, isVisible, pushes } = sidebarLayout(sidebarMode(isWide), drawerOpen);

  const close = useCallback(() => setDrawerOpen(false), []);

  /* In fixed mode there is nothing to toggle: the hamburger is not rendered and
     Ctrl/Cmd+B does nothing. */
  const toggle = useCallback(() => {
    if (isFixed) {
      return;
    }
    setDrawerOpen((open) => !open);
  }, [isFixed]);

  useEffect(() => {
    forgetRetiredPreference(typeof window === 'undefined' ? undefined : window.localStorage);
  }, []);

  useEffect(() => {
    let query: MediaQueryList;
    try {
      query = window.matchMedia(FIXED_QUERY);
    } catch {
      return;
    }
    const onChange = () => setIsWide(query.matches);
    onChange();
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, []);

  /* Crossing 80rem either way starts the drawer closed. */
  const wasWide = useRef(isWide);
  useEffect(() => {
    if (wasWide.current === isWide) {
      return;
    }
    wasWide.current = isWide;
    close();
  }, [isWide, close]);

  /* Any navigation closes the drawer. */
  const previousPath = useRef(pathname);
  useEffect(() => {
    if (previousPath.current === pathname) {
      return;
    }
    previousPath.current = pathname;
    close();
  }, [pathname, close]);

  /* A press outside the open drawer (the scrim included) closes it. */
  useEffect(() => {
    if (!isDrawer || !isVisible) {
      return;
    }
    function onPointerDown(event: PointerEvent) {
      const target = event.target as Node;
      if (panelRef.current?.contains(target) || menuButtonRef.current?.contains(target)) {
        return;
      }
      close();
    }
    document.addEventListener('pointerdown', onPointerDown);
    return () => document.removeEventListener('pointerdown', onPointerDown);
  }, [isDrawer, isVisible, close]);

  useEffect(() => {
    if (!isDrawer || !isVisible) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || hasOpenDialog() || hasOpenMenu()) {
        return;
      }
      event.stopPropagation();
      close();
    }
    window.addEventListener('keydown', onKeyDown, true);
    return () => window.removeEventListener('keydown', onKeyDown, true);
  }, [isDrawer, isVisible, close]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) {
        return;
      }
      if (event.key.toLowerCase() !== 'b') {
        return;
      }
      if (isFixed || isTypingTarget(event.target) || hasOpenDialog()) {
        return;
      }
      event.preventDefault();
      toggle();
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isFixed, toggle]);

  const value = useMemo<SidebarContextValue>(
    () => ({
      isVisible,
      pushes,
      isDrawer,
      isFixed,
      menuId,
      menuButtonRef,
      panelRef,
      toggle,
      close,
    }),
    [isVisible, pushes, isDrawer, isFixed, menuId, toggle, close],
  );

  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}
