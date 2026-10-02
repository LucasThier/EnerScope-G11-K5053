import { useEffect, useId, useRef, type ReactNode } from 'react';
import { CloseIcon } from './icons';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  /** `lg` is for a panel that carries a table rather than a form. */
  size?: 'md' | 'lg';
  children: ReactNode;
}

const SIZES = { md: 'max-w-lg', lg: 'max-w-2xl' };

/**
 * Focus-trap candidates. Disabled controls are excluded on purpose:
 * `NewProjectModal` renders its organization `<select>` disabled while the
 * options load, and the browser refuses focus on a disabled element — leaving
 * it in the list would stall Tab on a stop it can never reach.
 */
const FOCUSABLE = [
  'a[href]',
  'button:not(:disabled)',
  'input:not(:disabled)',
  'select:not(:disabled)',
  'textarea:not(:disabled)',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

/**
 * The focusable elements inside the panel, in document order, queried fresh on
 * every call rather than captured when the modal opened. Both modals rebuild
 * their contents after mounting — one while its organizations load, the other
 * while it fetches members — so a captured list would go stale.
 */
function focusablesIn(panel: HTMLElement): HTMLElement[] {
  return Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => element.getClientRects().length > 0,
  );
}

export function Modal({ open, onClose, title, size = 'md', children }: ModalProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      document.body.style.overflow = previousOverflow;
    };
  }, [open, onClose]);

  // Focus management: remember what had focus, move into the panel, keep Tab
  // inside it, and hand focus back on close.
  //
  // Kept separate from the Escape effect above, and depending on `open` alone.
  // Nothing here may come to depend on `onClose`: it is an inline arrow at both
  // call sites, so a new identity on every parent render would re-run this
  // cleanup and throw focus back to the page while the modal is still open.
  useEffect(() => {
    if (!open) {
      return;
    }
    const panel = panelRef.current;
    const opener = document.activeElement as HTMLElement | null;

    const first = panel ? focusablesIn(panel)[0] : null;
    (first ?? panel)?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Tab' || !panel) {
        return;
      }
      const focusables = focusablesIn(panel);
      if (focusables.length === 0) {
        // Unreachable while the close button renders, but a panel with nothing
        // to focus still must not leak Tab to the page behind it.
        event.preventDefault();
        panel.focus();
        return;
      }
      const firstFocusable = focusables[0];
      const lastFocusable = focusables[focusables.length - 1];
      const active = document.activeElement;

      // Focus escaped the panel — through the browser chrome, or a programmatic
      // focus() elsewhere. Pull it back to the edge Tab was heading for.
      if (!panel.contains(active)) {
        event.preventDefault();
        (event.shiftKey ? lastFocusable : firstFocusable).focus();
        return;
      }

      // Only the two edges are handled. Inside the panel the browser's own tab
      // order is left alone, because it reads the live DOM better than a
      // hand-kept index would.
      if (event.shiftKey && active === firstFocusable) {
        event.preventDefault();
        lastFocusable.focus();
      } else if (!event.shiftKey && active === lastFocusable) {
        event.preventDefault();
        firstFocusable.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      // The opener can be gone by now — a table row removed while the modal was
      // open — and focusing a detached node silently does nothing.
      if (opener?.isConnected) {
        opener.focus();
      }
    };
  }, [open]);

  if (!open) {
    return null;
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-start justify-center overflow-y-auto bg-ink-800/40 p-4 py-8"
      onMouseDown={(event) => {
        if (event.target === event.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={
          `w-full ${SIZES[size]} rounded-2xl border border-ink-100 bg-white ` +
          'shadow-md focus:outline-none'
        }
      >
        <div className="flex items-start justify-between gap-4 border-b border-ink-100 px-6 py-4">
          <h2 id={titleId} className="text-lg font-semibold text-ink-800">
            {title}
          </h2>
          <button
            type="button"
            onClick={onClose}
            aria-label="Cerrar"
            className={
              'shrink-0 rounded-lg p-1 text-ink-500 transition-colors hover:bg-ink-50 hover:text-ink-700 ' +
              'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
            }
          >
            <CloseIcon className="h-5 w-5" />
          </button>
        </div>
        <div className="p-6">{children}</div>
      </div>
    </div>
  );
}
