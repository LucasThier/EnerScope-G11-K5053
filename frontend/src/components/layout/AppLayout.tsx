import { Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { Sidebar } from './Sidebar';
import { RoleBadge } from '../ui/RoleBadge';
import { Button } from '../ui/Button';

/**
 * Signed-in shell: a role-aware sidebar for navigation plus a top bar with the
 * current user and logout. Page content renders through the router `Outlet`, so
 * the sidebar persists across navigation.
 */
export function AppLayout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  async function handleLogout() {
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div className="flex min-h-screen bg-cream">
      <Sidebar />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="flex h-16 items-center justify-end gap-4 border-b border-ink-100 bg-white px-6">
          {user && (
            <div className="hidden items-center gap-2 sm:flex">
              <span className="text-sm text-ink-500">{user.mail}</span>
              <RoleBadge role={user.platformRole} />
            </div>
          )}
          <Button variant="ghost" onClick={handleLogout}>
            Log out
          </Button>
        </header>
        <main className="mx-auto w-full max-w-4xl flex-1 px-6 py-8">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
