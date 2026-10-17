import { useEffect } from 'react';
import { useTheme } from '../../hooks/useTheme';
import lightSmall from '../../assets/auth/chain-light-768.webp';
import lightLarge from '../../assets/auth/chain-light-1536.webp';
import darkSmall from '../../assets/auth/chain-dark-768.webp';
import darkLarge from '../../assets/auth/chain-dark-1672.webp';

const ART = {
  light: { small: lightSmall, large: lightLarge, width: 1536 },
  dark: { small: darkSmall, large: darkLarge, width: 1672 },
};

export function LoginScene() {
  const { resolved } = useTheme();
  const art = ART[resolved];

  useEffect(() => {
    const alternate = new Image();
    alternate.fetchPriority = 'low';
    alternate.sizes = '100vw';
    alternate.srcset = `${ART[resolved === 'dark' ? 'light' : 'dark'].small} 768w, ${ART[resolved === 'dark' ? 'light' : 'dark'].large} ${ART[resolved === 'dark' ? 'light' : 'dark'].width}w`;
  }, [resolved]);

  return (
    <figure aria-label="Cadena de valor del GNL: Extracción, Transporte, Licuefacción y Exportación" className="pointer-events-none absolute inset-0 overflow-hidden">
      <img
        src={art.large}
        srcSet={`${art.small} 768w, ${art.large} ${art.width}w`}
        sizes="100vw"
        width="1536"
        height="864"
        alt=""
        aria-hidden="true"
        fetchPriority="high"
        decoding="async"
        className={`absolute inset-0 w-full select-none object-cover h-full object-left`}
      />
      <div className={`absolute inset-x-0 top-0 bg-linear-to-b from-auth-page/95 via-auth-page/90 via-55% to-transparent h-[50%]`} />
      <figcaption className="sr-only">Extracción, transporte, licuefacción y exportación de GNL.</figcaption>
    </figure>
  );
}
