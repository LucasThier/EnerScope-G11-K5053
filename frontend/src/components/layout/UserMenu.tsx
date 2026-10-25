import { useEffect, useId, useRef, type KeyboardEvent } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useDismissable } from '../../hooks/useDismissable';
import { Avatar } from '../ui/Avatar';
import { ChevronDownIcon } from '../ui/icons';

const ITEM_CLASSES =
  'block w-full px-4 py-2 text-left text-sm text-ink-700 transition-colors hover:bg-ink-50 ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus';

/**
 * The signed-in user in the top bar, and the menu behind them. The subtitle is
 * the free-form `jobTitle`; accounts created before it existed have none, so it
 * falls back to the platform role rather than leaving an empty line.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const { isOpen, ref, close, toggle } = useDismissable<HTMLDivElement>();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const menuId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    if (isOpen) {
      itemRefs.current[0]?.focus();
    }
  }, [isOpen]);

  if (!user) {
    return null;
  }

  const subtitle = user.jobTitle ?? (user.platformRole === 'ADMIN' ? 'Administrador' : 'Usuario');

  function closeAndFocusTrigger() {
    close();
    triggerRef.current?.focus();
  }

  async function handleLogout() {
    close();
    await logout();
    navigate('/login', { replace: true });
  }

  function focusItem(index: number) {
    const items = itemRefs.current;
    const count = items.length;
    items[((index % count) + count) % count]?.focus();
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const current = itemRefs.current.indexOf(document.activeElement as HTMLButtonElement);
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        focusItem(current + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        focusItem(current - 1);
        break;
      case 'Home':
        event.preventDefault();
        focusItem(0);
        break;
      case 'End':
        event.preventDefault();
        focusItem(itemRefs.current.length - 1);
        break;
      case 'Escape':
        event.preventDefault();
        closeAndFocusTrigger();
        break;
      case 'Tab':
        close();
        break;
      default:
        break;
    }
  }

  return (
    <div ref={ref} className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={toggle}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        aria-label={`${user.firstName} ${user.lastName}, menú de usuario`}
        className={
          'flex items-center gap-3 rounded-lg py-1 pl-1 pr-3 transition-colors hover:bg-ink-50 ' +
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
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
          id={menuId}
          role="menu"
          aria-label="Menú de usuario"
          onKeyDown={handleMenuKeyDown}
          className="absolute right-0 z-20 mt-1 w-64 rounded-xl border border-ink-100 bg-raised py-1 shadow-lg"
        >
          <div className="flex items-center gap-3 border-b border-ink-100 px-4 py-3">
            <Avatar firstName={user.firstName} lastName={user.lastName} />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink-800">
                {user.firstName} {user.lastName}
              </p>
              <p className="truncate text-xs text-ink-500">{user.mail}</p>
            </div>
          </div>
          <button
            ref={(el) => {
              itemRefs.current[0] = el;
            }}
            type="button"
            role="menuitem"
            aria-current={pathname === '/profile' ? 'page' : undefined}
            onClick={() => {
              close();
              navigate('/profile');
            }}
            className={ITEM_CLASSES}
          >
            Mi perfil
          </button>
          <hr className="my-1 border-t border-ink-100" />
          <button
            ref={(el) => {
              itemRefs.current[1] = el;
            }}
            type="button"
            role="menuitem"
            onClick={() => void handleLogout()}
            className={ITEM_CLASSES}
          >
            Cerrar sesión
          </button>
        </div>
      )}
    </div>
  );
}
