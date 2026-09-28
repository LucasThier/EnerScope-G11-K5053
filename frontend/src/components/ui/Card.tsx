import type { ReactNode } from 'react';

interface CardProps {
  children: ReactNode;
  /** `md` is for a card that carries a page on its own, like sign-in. */
  elevation?: 'sm' | 'md';
  /** Off for content that runs to the card's edges, like a table. */
  padded?: boolean;
  className?: string;
}

const ELEVATIONS = { sm: 'shadow-sm', md: 'shadow-md' };

/** Card surface: white on the ink-50 page, lifted by a border and a soft
 * shadow so it reads as a sheet rather than merging with the background. */
export function Card({ children, elevation = 'sm', padded = true, className = '' }: CardProps) {
  return (
    <div
      className={
        `rounded-2xl border border-ink-100 bg-white ${padded ? 'p-6' : ''} ` +
        `${ELEVATIONS[elevation]} ${className}`
      }
    >
      {children}
    </div>
  );
}
