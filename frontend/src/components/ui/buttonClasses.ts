/*
 * The look of a button, written once so that what is not a <button> but must
 * look like one (see ButtonLink) cannot drift from the real thing, in the same
 * way controlClasses.ts holds the form controls.
 */
export type ButtonVariant = 'primary' | 'secondary' | 'danger' | 'ghost' | 'outline' | 'link' | 'on-dark';
export type ButtonSize = 'md' | 'compact';
type Variant = ButtonVariant;
type Size = ButtonSize;


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
  /* A secondary action that must not outweigh the primary one on its screen: an edge, no fill. */
  outline:
    'border border-control-border bg-transparent text-ink-700 hover:bg-ink-50 hover:text-ink-800 focus-visible:ring-focus',
  'on-dark':
    'bg-spotlight-action text-spotlight-action-text hover:bg-spotlight-action/90 ' +
    'focus-visible:ring-white focus-visible:ring-offset-spotlight!',
  link:
    'bg-transparent p-0 text-brand-800 underline underline-offset-2 hover:text-brand-900 ' +
    'focus-visible:ring-focus',
};

/** The classes of a button, for what looks like one but is not a `<button>` (see `ButtonLink`). */
export function buttonClasses(variant: Variant = 'primary', size: Size = 'md'): string {
  const box = variant === 'link' ? '' : sizes[size];
  return `${base} ${box} ${variants[variant]}`;
}
