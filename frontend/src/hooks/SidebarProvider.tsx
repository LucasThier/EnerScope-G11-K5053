import { useCallback, useEffect, useId, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';
import { useLocalPreference } from './useLocalPreference';
import { SidebarContext, type SidebarContextValue, type SidebarOrigin } from './useSidebar';

const PINNED_KEY = 'sidebarPinned';
const DESKTOP_QUERY = '(min-width: 64rem)';
const PIN_QUERY = '(min-width: 80rem)';
const HOME_PATH = '/app';
const ENTRY_PATH = '/';
const EDGE_TRIGGER_PX = 4;
const TOP_BAR_PX = 64;
const HOVER_INTENT_MS = 150;
const HOVER_LEAVE_MS = 300;

function readQuery(query: string): boolean {
  try {
    return window.matchMedia(query).matches;
  } catch {
    return true;
  }
}

function readDesktop(): boolean {
  return readQuery(DESKTOP_QUERY);
}

function readWide(): boolean {
  return readQuery(PIN_QUERY);
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

export function SidebarProvider({ children }: { children: ReactNode }) {
  const { pathname } = useLocation();
  const [pinnedPreference, setPinned] = useLocalPreference<boolean>(PINNED_KEY, true);
  const isPinned = pinnedPreference !== false;
  const [origin, setOrigin] = useState<SidebarOrigin | null>(() =>
    !(isPinned && readWide()) && pathname === HOME_PATH && readDesktop() ? 'route' : null,
  );
  const [isDesktop, setIsDesktop] = useState(readDesktop);
  const [isWide, setIsWide] = useState(readWide);

  const menuButtonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLElement>(null);
  const leaveTimer = useRef<number | null>(null);
  const menuId = useId();

  const isDrawer = !isDesktop;
  const isFixed = isDesktop && isWide && isPinned;
  const isVisible = isFixed || origin !== null;
  const pushes = isFixed || (isDesktop && origin === 'route');

  const clearLeaveTimer = useCallback(() => {
    if (leaveTimer.current !== null) {
      window.clearTimeout(leaveTimer.current);
      leaveTimer.current = null;
    }
  }, []);

  const close = useCallback(() => {
    clearLeaveTimer();
    setOrigin(null);
  }, [clearLeaveTimer]);

  const open = useCallback(
    (next: SidebarOrigin) => {
      clearLeaveTimer();
      setOrigin(next);
    },
    [clearLeaveTimer],
  );

  const toggleFromButton = useCallback(
    (viaKeyboard: boolean) => {
      clearLeaveTimer();
      if (isFixed) {
        setPinned(false);
        setOrigin(null);
        return;
      }
      if (isVisible) {
        setOrigin(null);
        return;
      }
      setOrigin(viaKeyboard ? 'keyboard' : 'pointer');
    },
    [isFixed, isVisible, setPinned, clearLeaveTimer],
  );

  const togglePin = useCallback(
    (viaKeyboard: boolean) => {
      clearLeaveTimer();
      if (isPinned) {
        setPinned(false);
        if (viaKeyboard) {
          setOrigin('keyboard');
          return;
        }
        if (document.activeElement instanceof HTMLElement) {
          document.activeElement.blur();
        }
        setOrigin('pointer');
        return;
      }
      setPinned(true);
      setOrigin(null);
    },
    [isPinned, setPinned, clearLeaveTimer],
  );

  const keepOpen = useCallback(() => {
    clearLeaveTimer();
    setOrigin((current) => (current === 'hover' ? 'pointer' : current));
  }, [clearLeaveTimer]);

  const scheduleClose = useCallback(() => {
    if (leaveTimer.current !== null) {
      return;
    }
    leaveTimer.current = window.setTimeout(() => {
      leaveTimer.current = null;
      if (panelRef.current?.contains(document.activeElement)) {
        return;
      }
      setOrigin((current) => (current === 'hover' || current === 'pointer' ? null : current));
    }, HOVER_LEAVE_MS);
  }, []);

  useEffect(() => clearLeaveTimer, [clearLeaveTimer]);

  useEffect(() => {
    let query: MediaQueryList;
    let pinQuery: MediaQueryList;
    try {
      query = window.matchMedia(DESKTOP_QUERY);
      pinQuery = window.matchMedia(PIN_QUERY);
    } catch {
      return;
    }
    const onChange = () => {
      setIsDesktop(query.matches);
      setIsWide(pinQuery.matches);
    };
    onChange();
    query.addEventListener('change', onChange);
    pinQuery.addEventListener('change', onChange);
    return () => {
      query.removeEventListener('change', onChange);
      pinQuery.removeEventListener('change', onChange);
    };
  }, []);

  const wasWide = useRef(isWide);
  useEffect(() => {
    if (wasWide.current === isWide) {
      return;
    }
    wasWide.current = isWide;
    close();
  }, [isWide, close]);

  const wasDesktop = useRef(isDesktop);
  useEffect(() => {
    if (wasDesktop.current === isDesktop) {
      return;
    }
    wasDesktop.current = isDesktop;
    close();
  }, [isDesktop, close]);

  const previousPath = useRef(pathname);
  const hasEntered = useRef(pathname !== ENTRY_PATH);
  useEffect(() => {
    if (previousPath.current === pathname) {
      return;
    }
    previousPath.current = pathname;
    if (!hasEntered.current) {
      hasEntered.current = true;
      if (isDesktop && !isFixed && pathname === HOME_PATH) {
        setOrigin('route');
        return;
      }
    }
    close();
  }, [pathname, close, isDesktop, isFixed]);

  useEffect(() => {
    if (!isDesktop || isVisible) {
      return;
    }
    let intentTimer: number | null = null;
    const cancel = () => {
      if (intentTimer !== null) {
        window.clearTimeout(intentTimer);
        intentTimer = null;
      }
    };
    function onPointerMove(event: PointerEvent) {
      if (event.pointerType !== 'mouse' || event.buttons !== 0) {
        cancel();
        return;
      }
      if (event.clientX > EDGE_TRIGGER_PX || event.clientY <= TOP_BAR_PX) {
        cancel();
        return;
      }
      if (intentTimer === null) {
        intentTimer = window.setTimeout(() => {
          intentTimer = null;
          open('hover');
        }, HOVER_INTENT_MS);
      }
    }
    window.addEventListener('pointermove', onPointerMove);
    return () => {
      window.removeEventListener('pointermove', onPointerMove);
      cancel();
    };
  }, [isDesktop, isVisible, open]);

  useEffect(() => {
    if (!isDesktop || (origin !== 'hover' && origin !== 'pointer')) {
      return;
    }
    function onPointerMove(event: PointerEvent) {
      if (event.pointerType !== 'mouse') {
        return;
      }
      const panel = panelRef.current;
      if (!panel) {
        return;
      }
      const bounds = panel.getBoundingClientRect();
      const inside =
        event.clientX >= bounds.left &&
        event.clientX <= bounds.right &&
        event.clientY >= bounds.top &&
        event.clientY <= bounds.bottom;
      if (inside) {
        clearLeaveTimer();
        setOrigin((current) => (current === 'hover' ? 'pointer' : current));
        return;
      }
      scheduleClose();
    }
    window.addEventListener('pointermove', onPointerMove);
    return () => window.removeEventListener('pointermove', onPointerMove);
  }, [isDesktop, origin, clearLeaveTimer, scheduleClose]);

  useEffect(() => {
    if (!isVisible || pushes) {
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
  }, [isVisible, pushes, close]);

  useEffect(() => {
    if (!isVisible || isFixed) {
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
  }, [isVisible, isFixed, close]);

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (!(event.ctrlKey || event.metaKey) || event.altKey || event.shiftKey) {
        return;
      }
      if (event.key.toLowerCase() !== 'b') {
        return;
      }
      if (isTypingTarget(event.target) || hasOpenDialog()) {
        return;
      }
      event.preventDefault();
      toggleFromButton(true);
    }
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [toggleFromButton]);

  const value = useMemo<SidebarContextValue>(
    () => ({
      origin,
      isVisible,
      pushes,
      isDrawer,
      isPinned,
      isFixed,
      canPin: isWide,
      menuId,
      menuButtonRef,
      panelRef,
      toggleFromButton,
      togglePin,
      close,
      keepOpen,
    }),
    [
      origin,
      isVisible,
      pushes,
      isDrawer,
      isPinned,
      isFixed,
      isWide,
      menuId,
      toggleFromButton,
      togglePin,
      close,
      keepOpen,
    ],
  );

  return <SidebarContext.Provider value={value}>{children}</SidebarContext.Provider>;
}
