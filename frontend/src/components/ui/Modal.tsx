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

const FOCUSABLE = 'input, select, textarea, button, [href], [tabindex]:not([tabindex="-1"])';

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

  useEffect(() => {
    if (!open) {
      return;
    }
    const first = panelRef.current?.querySelector<HTMLElement>(FOCUSABLE);
    (first ?? panelRef.current)?.focus();
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
