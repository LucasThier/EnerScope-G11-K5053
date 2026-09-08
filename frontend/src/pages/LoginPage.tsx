import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { LoginForm } from '../components/auth/LoginForm';
import { Card } from '../components/ui/Card';
import { Logo } from '../components/ui/Logo';
import { PageLoader } from '../components/ui/PageLoader';

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
    <div className="flex min-h-screen flex-col items-center justify-center bg-cream px-6 py-12">
      <div className="w-full max-w-sm">
        <div className="mb-8 flex justify-center">
          <Logo />
        </div>
        <Card>
          <h1 className="text-xl font-semibold text-ink-700">Sign in</h1>
          <p className="mt-1 mb-6 text-sm text-ink-400">
            Welcome back. Enter your credentials to continue.
          </p>
          <LoginForm onSuccess={() => navigate('/', { replace: true })} />
        </Card>
        <p className="mt-6 text-center text-xs text-ink-400">
          Accounts are created by your administrator or organization owner.
        </p>
      </div>
    </div>
  );
}
