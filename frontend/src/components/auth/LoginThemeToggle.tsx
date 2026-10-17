import { useTheme } from '../../hooks/useTheme';

export function LoginThemeToggle() {
  const { resolved, setPreference } = useTheme();
  const dark = resolved === 'dark';

  return (
    <button
      type="button"
      aria-label={dark ? 'Activar modo claro' : 'Activar modo oscuro'}
      aria-pressed={dark}
      onClick={() => setPreference(dark ? 'light' : 'dark')}
      className="inline-flex shrink-0 min-h-11 min-w-11 justify-center items-center gap-2 rounded-full border border-auth-copy/40 px-3 text-sm sm:px-4 font-medium text-auth-heading transition-colors hover:bg-auth-heading/5 focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-auth-accent motion-reduce:transition-none"
    >
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" className="h-4 w-4" aria-hidden="true">
        {dark ? <><circle cx="12" cy="12" r="4" /><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5" /></> : <path d="M20.5 13.1A8.6 8.6 0 0 1 10.9 3.5a8.6 8.6 0 1 0 9.6 9.6Z" />}
      </svg>
      <span className="hidden sm:inline">{dark ? 'Modo claro' : 'Modo oscuro'}</span>
    </button>
  );
}
