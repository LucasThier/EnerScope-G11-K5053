interface LogoProps {
  withWordmark?: boolean;
  className?: string;
}

const SIZES = { mark: 'h-11 w-11', wordmark: 'text-2xl' };

/**
 * EnerScope brand mark: a dark rounded tile with the logo's node-link motif in
 * the two brand greens, optionally followed by the "EnerScope" wordmark (with a
 * green "S", as in the logo).
 */
export function Logo({ withWordmark = true, className = '' }: LogoProps) {
  return (
    <div className={`flex items-center gap-3 ${className}`}>
      <div className={`flex ${SIZES.mark} items-center justify-center rounded-xl bg-ink-700`}>
        <svg viewBox="0 0 32 32" className="h-7 w-7" fill="none" aria-hidden="true">
          <line x1="9" y1="20" x2="23" y2="12" stroke="var(--color-brand-500)" strokeWidth="2.5" strokeLinecap="round" />
          <circle cx="9" cy="20" r="4" fill="var(--color-brand-700)" />
          <circle cx="23" cy="12" r="4" fill="var(--color-brand-500)" />
        </svg>
      </div>
      {withWordmark && (
        <span className={`font-semibold tracking-tight text-ink-700 ${SIZES.wordmark}`}>
          Ener<span className="text-brand-500">S</span>cope
        </span>
      )}
    </div>
  );
}
