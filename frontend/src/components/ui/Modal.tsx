import { useEffect, useId, useRef, type ReactNode } from 'react';
import { CloseIcon } from './icons';

interface ModalProps {
  open: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  /** `lg` is for a panel that carries a table rather than a form. */
  size?: 'md' | 'lg';
  children: ReactNode;
}

const SIZES = { md: 'max-w-lg', lg: 'max-w-2xl' };

const FOCUSABLE = [
  'a[href]',
  'button:not(:disabled)',
  'input:not(:disabled)',
  'select:not(:disabled)',
  'textarea:not(:disabled)',
  '[tabindex]:not([tabindex="-1"])',
].join(', ');

function focusablesIn(panel: HTMLElement): HTMLElement[] {
  return Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (element) => element.getClientRects().length > 0,
  );
}

export function Modal({ open, onClose, title, subtitle, size = 'md', children }: ModalProps) {
  const titleId = useId();
  const panelRef = useRef<HTMLDivElement>(null);
  const bodyRef = useRef<HTMLDivElement>(null);

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

  useEffect(() => {
    if (!open) {
      return;
    }
    const panel = panelRef.current;
    const opener = document.activeElement as HTMLElement | null;

    const body = bodyRef.current;
    const first = body ? focusablesIn(body)[0] : null;
    (first ?? panel)?.focus();

    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Tab' || !panel) {
        return;
      }
      const focusables = focusablesIn(panel);
      if (focusables.length === 0) {
        event.preventDefault();
        panel.focus();
        return;
      }
      const firstFocusable = focusables[0];
      const lastFocusable = focusables[focusables.length - 1];
      const index = focusables.indexOf(document.activeElement as HTMLElement);

      if (index === -1) {
        event.preventDefault();
        (event.shiftKey ? lastFocusable : firstFocusable).focus();
        return;
      }

      if (event.shiftKey && index === 0) {
        event.preventDefault();
        lastFocusable.focus();
      } else if (!event.shiftKey && index === focusables.length - 1) {
        event.preventDefault();
        firstFocusable.focus();
      }
    }

    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
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
          <div className="min-w-0">
            <h2 id={titleId} className="text-lg font-semibold text-ink-800">
              {title}
            </h2>
            {subtitle && <p className="mt-0.5 truncate text-sm text-ink-500">{subtitle}</p>}
          </div>
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
        <div ref={bodyRef} className="p-6">
          {children}
        </div>
      </div>
    </div>
  );
}
