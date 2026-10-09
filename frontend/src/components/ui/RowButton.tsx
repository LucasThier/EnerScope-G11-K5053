import type { ReactNode } from 'react';

interface RowButtonProps {
  /** Accessible name, read by screen readers — kept specific to the row (e.g. "Cambiar el rol de Jane Doe"). */
  label: string;
  /** Visual tooltip. Defaults to `label` when omitted; pass a short action name to keep the tooltip generic while `label` stays descriptive. */
  title?: string;
  onClick: () => void;
  children: ReactNode;
}

export function RowButton({ label, title, onClick, children }: RowButtonProps) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={title ?? label}
      className={
        'inline-flex rounded-lg p-1 text-ink-500 transition-colors hover:bg-ink-50 ' +
        'hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
      }
    >
      {children}
    </button>
  );
}
