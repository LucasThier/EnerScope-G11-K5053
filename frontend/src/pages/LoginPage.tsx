import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { LoginForm } from '../components/auth/LoginForm';
import { Card } from '../components/ui/Card';
import { Logo } from '../components/ui/Logo';
import { NodeGraph } from '../components/ui/NodeGraph';
import { PageLoader } from '../components/ui/PageLoader';

const HEADLINE = 'Modelá toda la cadena de valor del GNL.';
const SUBHEAD = 'Un solo espacio para los proyectos, escenarios y costos de tu equipo.';

/** The three points carry their own numeral: it replaces the icon that used to
 * sit beside them and does the graphic work the icon was too small to do. */
const HIGHLIGHTS = [
  { number: '01', text: 'Mapeá la cadena de valor de un proyecto, nodo por nodo.' },
  { number: '02', text: 'Simulá escenarios sobre un proyecto versionado.' },
  { number: '03', text: 'Compará escenarios y sus costos en una sola vista.' },
];

export function LoginPage() {
  const { isAuthenticated, isLoading } = useAuth();
  const navigate = useNavigate();

  if (isLoading) {
    return <PageLoader />;
  }
  if (isAuthenticated) {
    return <Navigate to="/" replace />;
  }

  return (
    <div className="flex min-h-screen flex-col bg-ink-50 lg:flex-row">
      <BrandPanel />
      <div className="flex flex-1 items-center justify-center px-6 pt-10 pb-24">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex justify-center lg:hidden">
            <Logo variant="full" size="lg" />
          </div>
          <Card elevation="md">
            <h1 className="text-2xl font-semibold text-ink-800">Iniciar sesión</h1>
            <p className="mt-1 mb-6 text-sm text-ink-500">
              Ingresá tus credenciales para continuar.
            </p>
            <LoginForm onSuccess={() => navigate('/', { replace: true })} />
          </Card>
        </div>
      </div>
    </div>
  );
}

/**
 * Brand half of the sign-in screen: a solid ink-900 plate carrying an oversized
 * headline anchored to the bottom, with the value-chain network bleeding off
 * the top-right corner. The empty space sits above the type rather than below
 * it, which is what keeps the composition from reading as centred.
 *
 * It only appears from `lg` up, where the empty width is the problem it solves;
 * below that the layout collapses to the centred card with the logo above it.
 */
function BrandPanel() {
  return (
    <aside className="relative hidden overflow-hidden bg-ink-900 lg:flex lg:w-[52%] lg:max-w-3xl lg:flex-col lg:justify-between">
      <NodeGraph
        className="pointer-events-none absolute -right-20 -top-8 h-[38%] w-auto"
        lineClass="text-ink-700"
        nodeClass="text-ink-600"
        accentClass="text-brand-500"
      />

      {/* In the flow rather than positioned: the headline block grows with the
          copy, and an absolute logo would eventually collide with it. */}
      <div className="relative px-12 pt-12">
        <Logo variant="full" size="lg" tone="light" />
      </div>

      <div className="relative px-12 pb-16">
        <h2 className="max-w-[15ch] text-[clamp(2.75rem,3.4vw,3rem)] font-semibold leading-[1.02] tracking-[-0.03em] text-white">
          {HEADLINE}
        </h2>
        <p className="mt-6 max-w-lg text-xl leading-snug text-ink-300">{SUBHEAD}</p>
        <ul className="mt-8 max-w-xl border-t border-ink-700">
          {HIGHLIGHTS.map((item) => (
            <li key={item.number} className="flex items-baseline gap-6 border-b border-ink-700 py-3">
              <span className="w-12 shrink-0 text-2xl font-semibold tracking-tight text-brand-500">
                {item.number}
              </span>
              <span className="text-sm text-ink-300">{item.text}</span>
            </li>
          ))}
        </ul>
      </div>
    </aside>
  );
}
