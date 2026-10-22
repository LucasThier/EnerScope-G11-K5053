import { useSyncExternalStore } from 'react';
import { useTheme } from '../../hooks/useTheme';
import lightSmall from '../../assets/auth/chain-light-768.webp';
import lightLarge from '../../assets/auth/chain-light-1536.webp';
import darkSmall from '../../assets/auth/chain-dark-768.webp';
import darkLarge from '../../assets/auth/chain-dark-1672.webp';

const ART = {
  light: { small: lightSmall, large: lightLarge, width: 1536 },
  dark: { small: darkSmall, large: darkLarge, width: 1672 },
};

/** Below this the scene is not drawn at all, so the photograph is never
 *  fetched on a phone. One MediaQueryList for the module: the snapshot is read
 *  on every render and a fresh matchMedia per read allocates a listener target
 *  for nothing. */
const WIDE = '(min-width: 640px)';
const wideQuery = window.matchMedia(WIDE);

function subscribeToWidth(onChange: () => void) {
  wideQuery.addEventListener('change', onChange);
  return () => wideQuery.removeEventListener('change', onChange);
}

export function LoginScene() {
  const { resolved } = useTheme();
  const wide = useSyncExternalStore(subscribeToWidth, () => wideQuery.matches, () => false);

  if (!wide) return null;

  const art = ART[resolved];

  return (
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 overflow-hidden">
      <img
        src={art.large}
        srcSet={`${art.small} 768w, ${art.large} ${art.width}w`}
        sizes="100vw"
        width="1536"
        height="864"
        alt=""
        fetchPriority="high"
        decoding="async"
        className="absolute inset-0 h-full w-full select-none object-cover object-[left_80%]"
      />
      <div className="absolute inset-x-0 top-0 h-[50%] bg-linear-to-b from-auth-veil-top via-auth-veil-mid via-55% to-transparent" />
    </div>
  );
}
