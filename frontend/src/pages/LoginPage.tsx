import type { ReactNode } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { LoginForm } from '../components/auth/LoginForm';
import { Card } from '../components/ui/Card';
import { Logo } from '../components/ui/Logo';
import { PageLoader } from '../components/ui/PageLoader';
import { CompareIcon, FlaskIcon, ValueChainIcon } from '../components/ui/icons';

/** The three points reuse the navigation's own icons, so the panel describes
 * the product the sidebar actually lays out rather than inventing a pitch. */
const HIGHLIGHTS = [
  { icon: <ValueChainIcon />, text: 'Map the value chain of a project, node by node.' },
  { icon: <FlaskIcon />, text: 'Simulate scenarios over a versioned project.' },
  { icon: <CompareIcon />, text: 'Compare scenarios and their costs side by side.' },
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
      <div className="flex flex-1 items-center justify-center px-6 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex justify-center lg:hidden">
            <Logo variant="full" size="lg" />
          </div>
          <Card elevation="md">
            <h1 className="text-2xl font-semibold text-ink-800">Sign in</h1>
            <p className="mt-1 mb-6 text-sm text-ink-500">
              Welcome back. Enter your credentials to continue.
            </p>
            <LoginForm onSuccess={() => navigate('/', { replace: true })} />
          </Card>
          <p className="mt-6 text-center text-xs text-ink-500">
            Accounts are created by your administrator or organization owner.
          </p>
        </div>
      </div>
    </div>
  );
}

/**
 * Brand half of the sign-in screen. It only appears from `lg` up, where the
 * empty width is the problem it solves; below that the layout collapses to the
 * centered card, with the logo moving above it.
 */
function BrandPanel() {
  return (
    <aside className="hidden border-r border-ink-100 bg-linear-to-b from-brand-50 to-ink-50 px-12 py-16 lg:flex lg:w-[45%] lg:max-w-2xl lg:flex-col lg:justify-center">
      <Logo variant="full" size="lg" />
      <h2 className="mt-10 max-w-md text-2xl font-semibold tracking-tight text-ink-800">
        Model the LNG value chain, end to end.
      </h2>
      <p className="mt-2 max-w-md text-sm text-ink-600">
        One workspace for the projects, scenarios and costs your team already works with.
      </p>
      <ul className="mt-8 flex max-w-md flex-col gap-4">
        {HIGHLIGHTS.map((item) => (
          <Highlight key={item.text} icon={item.icon}>
            {item.text}
          </Highlight>
        ))}
      </ul>
    </aside>
  );
}

function Highlight({ icon, children }: { icon: ReactNode; children: ReactNode }) {
  return (
    <li className="flex items-start gap-3 text-sm text-ink-700">
      <span className="mt-0.5 shrink-0 text-brand-800" aria-hidden="true">
        {icon}
      </span>
      {children}
    </li>
  );
}
