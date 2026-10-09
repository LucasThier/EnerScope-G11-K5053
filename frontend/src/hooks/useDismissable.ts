import { useCallback, useEffect, useRef, useState } from 'react';

/**
 * Open/close state for a popover that closes on an outside click or Escape.
 * Shared by the top bar menus so both dismiss the same way; the returned ref
 * goes on the element that wraps both the trigger and the panel.
 */
export function useDismissable<T extends HTMLElement>() {
  const [isOpen, setIsOpen] = useState(false);
  const ref = useRef<T>(null);

  const close = useCallback(() => setIsOpen(false), []);
  const toggle = useCallback(() => setIsOpen((open) => !open), []);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    function onPointerDown(event: MouseEvent) {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        setIsOpen(false);
      }
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        setIsOpen(false);
      }
    }
    document.addEventListener('mousedown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('mousedown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [isOpen]);

  return { isOpen, ref, close, toggle };
}
