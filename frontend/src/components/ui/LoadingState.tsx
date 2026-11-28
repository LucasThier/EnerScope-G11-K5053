import type { ReactNode } from 'react';

interface LoadingStateProps {
  /** What is loading, e.g. "Cargando proyectos…". */
  children: ReactNode;
  className?: string;
}

/**
 * "Still loading" inside a card or a section, announced to screen readers as a
 * status. A whole screen waiting on the session is `PageLoader`; a spinner next
 * to a label is `Spinner`.
 */
export function LoadingState({ children, className = '' }: LoadingStateProps) {
  return (
    <p role="status" className={`px-4 py-8 text-center text-sm text-ink-500 ${className}`}>
      {children}
    </p>
  );
}
