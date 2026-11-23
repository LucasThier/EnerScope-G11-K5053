import type { ReactNode } from 'react';

interface EmptyStateProps {
  /** What is missing, and what to do about it, in a sentence. */
  children: ReactNode;
  /** The one button that fixes it, when the reader may use it. */
  action?: ReactNode;
  className?: string;
}

/**
 * "There is nothing here" inside a card or a section: centred, quiet, with the
 * way out underneath. For a list that is empty or a search that matches
 * nothing. Loading is `LoadingState`; a failure is an `Alert`.
 */
export function EmptyState({ children, action, className = '' }: EmptyStateProps) {
  return (
    <div className={`px-4 py-8 text-center text-sm text-ink-500 ${className}`}>
      {children}
      {action && <div className="mt-4">{action}</div>}
    </div>
  );
}
