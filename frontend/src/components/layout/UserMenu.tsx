import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useDismissable } from '../../hooks/useDismissable';
import { Avatar } from '../ui/Avatar';
import { ChevronDownIcon } from '../ui/icons';

/**
 * The signed-in user in the top bar, and the menu behind them. The subtitle is
 * the free-form `jobTitle`; accounts created before it existed have none, so it
 * falls back to the platform role rather than leaving an empty line.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const { isOpen, ref, close, toggle } = useDismissable<HTMLDivElement>();
  const navigate = useNavigate();

  if (!user) {
    return null;
  }

  const subtitle = user.jobTitle ?? (user.platformRole === 'ADMIN' ? 'Administrador' : 'Usuario');

  async function handleLogout() {
    close();
    await logout();
    navigate('/login', { replace: true });
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={toggle}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        className={
          'flex items-center gap-3 rounded-lg py-1 pl-1 pr-3 transition-colors hover:bg-ink-50 ' +
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
        }
      >
        <Avatar firstName={user.firstName} lastName={user.lastName} />
        <span className="hidden text-left sm:block">
          <span className="block text-sm font-semibold leading-tight text-ink-800">
            {user.firstName} {user.lastName}
          </span>
          <span className="block text-xs leading-tight text-ink-500">{subtitle}</span>
        </span>
        <ChevronDownIcon
          className={`hidden h-4 w-4 text-ink-400 transition-transform sm:block ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div
          role="menu"
          className="absolute right-0 z-20 mt-1 w-64 rounded-xl border border-ink-100 bg-white py-1 shadow-lg"
        >
          <div className="border-b border-ink-100 px-4 py-3">
            <p className="truncate text-sm font-semibold text-ink-800">
              {user.firstName} {user.lastName}
            </p>
            <p className="truncate text-xs text-ink-500">{user.mail}</p>
          </div>
          <button
            type="button"
            role="menuitem"
            onClick={() => void handleLogout()}
            className="block w-full px-4 py-2 text-left text-sm text-ink-700 transition-colors hover:bg-ink-50"
          >
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}
