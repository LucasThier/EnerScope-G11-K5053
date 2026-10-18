import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { useTheme } from '../hooks/useTheme';
import { LoginForm } from '../components/auth/LoginForm';
import { LoginScene } from '../components/auth/LoginScene';
import { Logo } from '../components/ui/Logo';
import { PageLoader } from '../components/ui/PageLoader';

export function LoginPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const { resolved } = useTheme();
  const navigate = useNavigate();

  if (isLoading) return <PageLoader />;
  if (isAuthenticated) return <Navigate to="/" replace />;

  return (
    <div className="relative isolate min-h-dvh overflow-x-clip bg-auth-page font-sans text-auth-heading">
      <LoginScene />
      <div className="relative mx-auto flex min-h-dvh max-w-[1680px] flex-col">
        <header className="relative z-10 flex items-center justify-between gap-4 px-6 py-[clamp(0.75rem,2vh,1.5rem)] sm:px-10 lg:px-12 xl:px-16">
          <Logo variant="full" size="sm" tone={resolved === 'dark' ? 'light' : 'dark'} />
        </header>

        <main className="grid flex-1 grid-cols-1 content-start pb-4 lg:grid-cols-[minmax(0,1.55fr)_minmax(0,1fr)] lg:items-start lg:gap-4 lg:pb-6 xl:gap-6">
          <section aria-labelledby="login-headline" className="max-lg:[@media(max-height:850px)]:sr-only px-6 pt-4 sm:px-10 sm:pt-8 lg:col-start-1 lg:row-start-1 lg:pl-12 lg:pr-0 lg:pt-8 xl:pl-16">
            <h1 id="login-headline" className="text-[clamp(2.125rem,4.75vw,4.625rem)] leading-[1.06] font-bold tracking-[-0.035em]">
              <span className="block">Toda la cadena.</span>
              <span className="block text-auth-accent">Una visión clara.</span>
            </h1>
            <p className="relative isolate mt-4 max-w-[34rem] before:pointer-events-none before:absolute before:-z-10 before:[inset:var(--auth-subtitle-halo-inset)] before:[border-radius:var(--auth-subtitle-halo-radius)] before:bg-auth-subtitle-halo before:[filter:blur(var(--auth-subtitle-halo-blur))] before:content-[''] text-base leading-relaxed text-auth-subtitle sm:text-xl sm:leading-7 xl:text-[1.375rem] xl:leading-8">
              Modelá la cadena de valor del GNL y seguí cada versión de tu proyecto.
            </p>
          </section>

          <section aria-labelledby="login-title" className="relative z-10 mx-4 mt-2 rounded-[28px] border border-auth-panel/40 bg-linear-to-br from-auth-panel/65 via-auth-panel/55 to-auth-panel/50 px-6 py-[clamp(1rem,3vh,3rem)] text-auth-panel-ink shadow-[0_20px_80px_-24px_var(--color-auth-shadow)] backdrop-blur-[6px] [color-scheme:light] max-sm:border-auth-divider/65 max-sm:bg-none max-sm:bg-auth-panel max-sm:backdrop-blur-none sm:mx-auto sm:w-[calc(100%-5rem)] sm:max-w-[440px] sm:px-10 lg:col-start-2 lg:row-start-1 lg:mx-0 lg:mt-4 lg:w-[calc(100%-2.5rem)] lg:max-w-[440px] lg:justify-self-end lg:mr-10 xl:mr-16 xl:w-[calc(100%-4rem)] xl:px-10 ">
            <h2 id="login-title" className="text-[clamp(1.875rem,2.8vw,2.5rem)] leading-tight font-bold tracking-[-0.035em]">Iniciar sesión</h2>
            <p className="mt-3 text-base leading-6 text-auth-panel-ink">Ingresá tus credenciales para continuar.</p>
            <LoginForm className="mt-[clamp(1rem,3vh,2.5rem)]" onSuccess={() => navigate('/', { replace: true })} />
            {/* The footer's grey is legible on the opaque phone panel but not on the
                glass one, where the photograph shows through behind 14px type, so
                from `sm` up — exactly where the panel turns to glass — both lines
                take the panel's ink. */}
            <div className="mt-[clamp(1rem,3vh,2.5rem)] border-t border-auth-divider/65 pt-[clamp(0.75rem,2vh,2rem)]">
              <h3 className="text-sm font-medium text-auth-panel-muted sm:text-auth-panel-ink-strong">¿Necesitás una cuenta?</h3>
              <p className="mt-1 text-sm leading-6 text-auth-panel-muted sm:text-auth-panel-ink-strong">Contactá al administrador de tu organización.</p>
            </div>
          </section>
        </main>
      </div>
    </div>
  );
}
