import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useDismissable } from '../../hooks/useDismissable';
import { useTheme, type ThemePreference } from '../../hooks/useTheme';
import { Avatar } from '../ui/Avatar';
import { ChevronDownIcon, MonitorIcon, MoonIcon, SunIcon } from '../ui/icons';

const ITEM_CLASSES =
  'block w-full px-4 py-2.5 text-left text-sm text-ink-700 transition-colors hover:bg-nav-hover ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-focus';

const THEME_CHOICES: { value: ThemePreference; label: string; icon: ReactNode }[] = [
  { value: 'light', label: 'Claro', icon: <SunIcon className="h-4 w-4" /> },
  { value: 'dark', label: 'Oscuro', icon: <MoonIcon className="h-4 w-4" /> },
  { value: 'system', label: 'Sistema', icon: <MonitorIcon className="h-4 w-4" /> },
];

/**
 * The signed-in user in the top bar, and the menu behind them. The subtitle is
 * the free-form `jobTitle`; accounts created before it existed have none, so it
 * falls back to the platform role rather than leaving an empty line.
 */
export function UserMenu() {
  const { user, logout } = useAuth();
  const { preference, setPreference } = useTheme();
  const { isOpen, ref, close, toggle } = useDismissable<HTMLDivElement>();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const menuId = useId();
  const themeLabelId = useId();
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
    const inThemeGroup = current >= 1 && current <= 3;
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        focusItem(inThemeGroup ? 4 : current + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        focusItem(inThemeGroup ? 0 : current - 1);
        break;
      case 'ArrowRight':
        if (inThemeGroup) {
          event.preventDefault();
          focusItem(current === 3 ? 1 : current + 1);
        }
        break;
      case 'ArrowLeft':
        if (inThemeGroup) {
          event.preventDefault();
          focusItem(current === 1 ? 3 : current - 1);
        }
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
          'flex h-11 items-center gap-2 rounded-xl p-1 transition-colors hover:bg-nav-hover ' +
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus lg:gap-3 lg:pr-3'
        }
      >
        <Avatar firstName={user.firstName} lastName={user.lastName} />
        <span className="hidden max-w-[12rem] text-left lg:block">
          <span className="block truncate text-sm font-semibold leading-tight text-ink-800">
            {user.firstName} {user.lastName}
          </span>
          <span className="block truncate text-xs leading-tight text-ink-500">{subtitle}</span>
        </span>
        <ChevronDownIcon
          className={`hidden h-4 w-4 shrink-0 text-ink-500 transition-transform sm:block ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div
          id={menuId}
          role="menu"
          aria-label="Menú de usuario"
          onKeyDown={handleMenuKeyDown}
          className={
            'absolute right-0 z-20 mt-2 w-72 max-w-[calc(100vw-1.5rem)] rounded-xl border ' +
            'border-ink-100 bg-raised py-1 shadow-float'
          }
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
          <p
            id={themeLabelId}
            className="px-4 pb-1.5 pt-1 text-[11px] font-semibold uppercase tracking-[0.08em] text-ink-500"
          >
            Tema
          </p>
          <div
            role="group"
            aria-labelledby={themeLabelId}
            className="mx-4 mb-2 flex gap-1 rounded-xl border border-ink-100 bg-nav p-1"
          >
            {THEME_CHOICES.map((choice, index) => {
              const isSelected = preference === choice.value;
              return (
                <button
                  key={choice.value}
                  ref={(el) => {
                    itemRefs.current[index + 1] = el;
                  }}
                  type="button"
                  role="menuitemradio"
                  aria-checked={isSelected}
                  onClick={() => setPreference(choice.value)}
                  className={
                    'flex flex-1 flex-col items-center gap-1 rounded-lg px-1 py-2 text-[11px] ' +
                    'transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
                    (isSelected
                      ? 'bg-nav-active font-semibold text-brand-900'
                      : 'font-medium text-ink-500 hover:bg-nav-hover hover:text-ink-700')
                  }
                >
                  {choice.icon}
                  {choice.label}
                </button>
              );
            })}
          </div>
          <hr className="my-1 border-t border-ink-100" />
          <button
            ref={(el) => {
              itemRefs.current[4] = el;
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
