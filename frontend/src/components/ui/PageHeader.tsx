import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

interface PageHeaderProps {
  /** The page's only `h1`. */
  title: ReactNode;
  /** One line under the title (`text-sm text-ink-500`). */
  description?: ReactNode;
  /** The "← volver" link above the title. `label` keeps its own arrow. */
  back?: { to: string; label: ReactNode };
  /** A short line above the title, like "Paso 1 de 2". */
  eyebrow?: ReactNode;
  /** Buttons or links on the right; below the title under `sm`. */
  actions?: ReactNode;
  /** Anything that belongs under the description (a note, a status line). */
  children?: ReactNode;
  /**
   * Off when the parent already spaces its children (`flex gap-*` or
   * `space-y-*`), so the header does not add its own bottom margin on top.
   */
  spaced?: boolean;
  className?: string;
}

/** The header of a regular page: back link, eyebrow, title, description and actions. */
export function PageHeader({
  title,
  description,
  back,
  eyebrow,
  actions,
  children,
  spaced = true,
  className = '',
}: PageHeaderProps) {
  const titleGap = eyebrow ? 'mt-1' : back ? 'mt-2' : '';
  return (
    <header
      className={
        `flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between ` +
        `${spaced ? 'mb-6' : ''} ${className}`
      }
    >
      <div className="min-w-0">
        {back && (
          <Link to={back.to} className="text-sm font-semibold text-brand-800 hover:underline">
            {back.label}
          </Link>
        )}
        {eyebrow && (
          <p className={`${back ? 'mt-3' : ''} text-sm font-semibold text-brand-800`}>{eyebrow}</p>
        )}
        <h1 className={`${titleGap} text-2xl font-semibold text-ink-800`}>{title}</h1>
        {description && <p className="mt-1 text-sm text-ink-500">{description}</p>}
        {children}
      </div>
      {actions && <div className="flex flex-wrap items-center gap-3">{actions}</div>}
    </header>
  );
}
