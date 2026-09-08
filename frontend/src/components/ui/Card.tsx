import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  className?: string;
}

/** White surface card with a soft border and shadow, on the cream background. */
export function Card({ children, className = '' }: CardProps) {
  return (
    <div className={`rounded-2xl border border-ink-100 bg-white p-6 shadow-sm ${className}`}>
      {children}
    </div>
  );
}
