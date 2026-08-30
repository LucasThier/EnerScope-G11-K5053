import type { ButtonHTMLAttributes, ReactNode } from 'react';
import { Spinner } from './Spinner';

type Variant = 'primary' | 'secondary' | 'ghost';

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  loading?: boolean;
  children: ReactNode;
}

const base =
  'inline-flex items-center justify-center gap-2 rounded-lg px-4 py-2.5 text-sm font-semibold transition-colors ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400 focus-visible:ring-offset-2 ' +
  'focus-visible:ring-offset-cream disabled:opacity-60 disabled:cursor-not-allowed';

const variants: Record<Variant, string> = {
  primary: 'bg-brand-500 text-white hover:bg-brand-600',
  secondary: 'bg-ink-700 text-white hover:bg-ink-800',
  ghost: 'bg-transparent text-ink-600 hover:bg-ink-100',
};

export function Button({
  variant = 'primary',
  loading = false,
  disabled,
  children,
  className = '',
  // Default to "button" so a button only submits a form when it opts in with
  // type="submit"; this avoids stray submits from secondary buttons.
  type = 'button',
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      className={`${base} ${variants[variant]} ${className}`}
      disabled={disabled || loading}
      {...rest}
    >
      {loading && <Spinner className="h-4 w-4" />}
      {children}
    </button>
  );
}
