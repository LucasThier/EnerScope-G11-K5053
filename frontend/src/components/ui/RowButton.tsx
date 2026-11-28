import type { ReactNode } from 'react';

interface RowButtonProps {
  /** Accessible name, read by screen readers — kept specific to the row (e.g. "Cambiar el rol de Jane Doe"). */
  label: string;
  /** Visual tooltip. Defaults to `label` when omitted; pass a short action name to keep the tooltip generic while `label` stays descriptive. */
  title?: string;
  onClick: () => void;
  disabled?: boolean;
  children: ReactNode;
}

export function RowButton({ label, title, onClick, disabled = false, children }: RowButtonProps) {
  return (
    <button
      type="button"
      onClick={() => {
        if (!disabled) {
          onClick();
        }
      }}
      aria-disabled={disabled}
      aria-label={label}
      title={title ?? label}
      className={
        'inline-flex rounded-control p-1 text-ink-500 transition-colors hover:bg-ink-50 ' +
        // 24px of button (p-1 + a 16px icon) + 10px above and below = a 44px-tall touch area. It is not
        // widened (inset-x-0): the buttons of a row sit 4px apart and a wider area would overlap.
        'relative after:absolute after:inset-x-0 after:-inset-y-2.5 ' +
        'hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
        (disabled
          ? 'cursor-not-allowed opacity-60 hover:bg-transparent hover:text-ink-500'
          : '')
      }
    >
      {children}
    </button>
  );
}
