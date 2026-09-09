import markUrl from '../../assets/logo-mark.png';
import fullUrl from '../../assets/logo-full.png';

interface LogoProps {
  /** `mark` is the "ES" monogram alone, for tight spots like the top bar.
   * `full` adds the wordmark beneath it, for the sign-in page. */
  variant?: 'mark' | 'full';
  size?: 'sm' | 'lg';
  className?: string;
}

/** Intrinsic size of each file, declared on the element so the browser reserves
 * the right box before the image loads and the header does not shift. */
const ART = {
  mark: { src: markUrl, width: 640, height: 332 },
  full: { src: fullUrl, width: 640, height: 465 },
};

const HEIGHTS = {
  mark: { sm: 'h-8', lg: 'h-12' },
  full: { sm: 'h-14', lg: 'h-20' },
};

/**
 * EnerScope brand mark. Sized by height only — the width follows the artwork's
 * own proportion, so it can never be squashed. Both files are keyed to
 * transparency, so they sit on the white chrome and on the ink-50 page alike.
 */
export function Logo({ variant = 'mark', size = 'sm', className = '' }: LogoProps) {
  const art = ART[variant];

  return (
    <img
      src={art.src}
      width={art.width}
      height={art.height}
      alt="EnerScope"
      className={`${HEIGHTS[variant][size]} w-auto object-contain ${className}`}
    />
  );
}
