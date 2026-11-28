import markUrl from '../../assets/logo-mark.png';
import markInverseUrl from '../../assets/logo-mark-inverse.png';
import fullUrl from '../../assets/logo-full.png';
import fullInverseUrl from '../../assets/logo-full-inverse.png';
import markSidebarUrl from '../../assets/logo-mark-sidebar.png';
import fullSidebarUrl from '../../assets/logo-full-sidebar.png';

interface LogoProps {
  /** `mark` is the "ES" monogram alone, for tight spots like the top bar.
   * `full` adds the wordmark beneath it, for the sign-in page. */
  variant?: 'mark' | 'full';
  /** `md` is the green sidebar's 64px logo cell (44px of art, 10px of air). */
  size?: 'sm' | 'md' | 'lg';
  /** Names the artwork, not the surface: `dark` is the charcoal original for
   * light backgrounds, `light` is the white cut for dark ones (the ink turns
   * white, the greens are untouched). `sidebar` is the white cut with its dark
   * greens lifted so every graphic colour clears 3:1 on the green sidebar
   * (#114636), where the swoosh and the node of `light` fall to 1.6-1.9:1. */
  tone?: 'dark' | 'light' | 'sidebar';
  className?: string;
}

/** Intrinsic size of each file, declared on the element so the browser reserves
 * the right box before the image loads and the header does not shift. */
const ART = {
  mark: { dark: markUrl, light: markInverseUrl, sidebar: markSidebarUrl, width: 640, height: 332 },
  full: { dark: fullUrl, light: fullInverseUrl, sidebar: fullSidebarUrl, width: 640, height: 465 },
};

const HEIGHTS = {
  mark: { sm: 'h-8', md: 'h-11', lg: 'h-12' },
  full: { sm: 'h-14', md: 'h-16', lg: 'h-20' }, // md: between the two, unused so far
};

/**
 * EnerScope brand mark. Sized by height only — the width follows the artwork's
 * own proportion, so it can never be squashed. Both files are keyed to
 * transparency, so they sit on the white chrome and on the ink-50 page alike;
 * `tone="light"` is the cut for the ink-900 surfaces and `tone="sidebar"` the
 * cut for the green rail.
 */
export function Logo({
  variant = 'mark',
  size = 'sm',
  tone = 'dark',
  className = '',
}: LogoProps) {
  const art = ART[variant];

  return (
    <img
      src={art[tone]}
      width={art.width}
      height={art.height}
      alt="EnerScope"
      className={`${HEIGHTS[variant][size]} w-auto object-contain ${className}`}
    />
  );
}
