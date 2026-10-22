import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useDismissable } from '../../hooks/useDismissable';
import { useTheme, type ThemePreference } from '../../hooks/useTheme';
import { Avatar } from '../ui/Avatar';
import { ChevronDownIcon, LogoutIcon, MonitorIcon, MoonIcon, SunIcon, UserIcon } from '../ui/icons';

const ITEM_CLASSES =
  'flex min-h-control-compact w-full items-center gap-3 rounded-control px-3 text-left text-sm ' +
  'font-medium transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
  'motion-reduce:transition-none';

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

  useEffect(() => {
    close();
  }, [pathname, close]);

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

  function handleTriggerKeyDown(event: KeyboardEvent<HTMLButtonElement>) {
    if (event.key === 'ArrowDown' && !isOpen) {
      event.preventDefault();
      toggle();
    }
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLElement>) {
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
        triggerRef.current?.focus();
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
        onKeyDown={handleTriggerKeyDown}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        aria-label={`${user.firstName} ${user.lastName}, ${subtitle}. Menú de usuario`}
        className={
          'flex h-11 items-center gap-1 rounded-full p-1 transition-colors hover:bg-nav-hover ' +
          'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus sm:pr-2 ' +
          'motion-reduce:transition-none ' +
          (isOpen ? 'bg-nav-hover' : '')
        }
      >
        <Avatar firstName={user.firstName} lastName={user.lastName} />
        <ChevronDownIcon
          className={`hidden h-4 w-4 shrink-0 text-ink-500 transition-transform motion-reduce:transition-none sm:block ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <div
          id={menuId}
          onKeyDown={handleMenuKeyDown}
          className={
            'absolute right-0 z-20 mt-2 w-80 max-w-[calc(100vw-1.5rem)] rounded-card border ' +
            'border-ink-100 bg-raised shadow-float transition duration-150 ease-out ' +
            'starting:-translate-y-1 starting:opacity-0 motion-reduce:transition-none ' +
            'motion-reduce:starting:translate-y-0'
          }
        >
          <div className="flex items-center gap-3 border-b border-ink-100 p-4">
            <Avatar firstName={user.firstName} lastName={user.lastName} size="lg" />
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold text-ink-800" title={`${user.firstName} ${user.lastName}`}>
                {user.firstName} {user.lastName}
              </p>
              <p className="truncate text-xs text-ink-500" title={user.mail}>
                {user.mail}
              </p>
              <p className="truncate text-xs text-ink-500">{subtitle}</p>
            </div>
          </div>
          <div role="menu" aria-label="Menú de usuario" className="p-2">
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
              className={`${ITEM_CLASSES} text-ink-700 hover:bg-nav-hover`}
            >
              <UserIcon className="h-4 w-4 shrink-0 text-ink-500" />
              Mi perfil
            </button>
            <hr className="my-2 border-t border-ink-100" />
            <p
              id={themeLabelId}
              className="px-3 pb-1.5 pt-1 text-xs font-semibold text-ink-500"
            >
              Apariencia
            </p>
            <div
              role="group"
              aria-labelledby={themeLabelId}
              className="mb-1 flex gap-1 rounded-control border border-ink-100 bg-nav p-1"
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
                      'flex h-control-compact flex-1 items-center justify-center gap-1.5 rounded-lg px-1 text-xs ' +
                      'transition-colors focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
                      'motion-reduce:transition-none ' +
                      (isSelected
                        ? 'bg-surface font-semibold text-brand-900 shadow-raise'
                        : 'font-medium text-ink-500 hover:bg-nav-hover hover:text-ink-700')
                    }
                  >
                    {choice.icon}
                    {choice.label}
                  </button>
                );
              })}
            </div>
            <hr className="my-2 border-t border-ink-100" />
            <button
              ref={(el) => {
                itemRefs.current[4] = el;
              }}
              type="button"
              role="menuitem"
              onClick={() => void handleLogout()}
              className={`${ITEM_CLASSES} text-danger-700 hover:bg-danger-50`}
            >
              <LogoutIcon className="h-4 w-4 shrink-0" />
              Cerrar sesión
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
