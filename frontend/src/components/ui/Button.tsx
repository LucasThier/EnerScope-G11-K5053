import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'link' | 'on-dark';
type Size = 'md' | 'compact';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
  loading?: boolean;
  children: ReactNode;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-control text-sm font-semibold ' +
  'transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-surface disabled:cursor-not-allowed disabled:opacity-60 ' +
  'motion-reduce:transition-none';

const sizes: Record<Size, string> = {
  md: 'h-control px-5',
  compact: 'h-control-compact px-4',
};

const variants: Record<Variant, string> = {
  primary: 'bg-brand-800 text-on-accent hover:bg-brand-900 focus-visible:ring-focus',
  secondary:
    'bg-fill-neutral text-on-accent hover:bg-fill-neutral-strong focus-visible:ring-focus',
  danger: 'bg-danger-600 text-on-accent hover:bg-danger-700 focus-visible:ring-danger-700',
  ghost: 'bg-transparent text-ink-600 hover:bg-ink-50 hover:text-ink-800 focus-visible:ring-focus',
  'on-dark':
    'bg-spotlight-action text-spotlight-action-text hover:bg-spotlight-action/90 ' +
    'focus-visible:ring-white focus-visible:ring-offset-spotlight!',
  link:
    'bg-transparent p-0 text-brand-800 underline underline-offset-2 hover:text-brand-900 ' +
    'focus-visible:ring-focus focus-visible:ring-offset-0',
};

export function Button({
  variant = 'primary',
  size = 'md',
  loading = false,
  disabled,
  children,
  className = '',
  // Default to "button" so a button only submits a form when it opts in with
  // type="submit"; this avoids stray submits from secondary buttons.
  type = 'button',
  ...rest
}: ButtonProps) {
  const box = variant === 'link' ? '' : sizes[size];
  return (
    <button
      type={type}
      className={`${base} ${box} ${variants[variant]} ${className}`}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...rest}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  );
}
