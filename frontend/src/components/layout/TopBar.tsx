import { useLocation } from 'react-router-dom';
import { useSidebar } from '../../hooks/useSidebar';
import { MenuIcon } from '../ui/icons';
import { Logo } from '../ui/Logo';
import { moduleTitleFor } from './navItems';
import { ProjectSwitcher } from './ProjectSwitcher';
import { UserMenu } from './UserMenu';

/**
 * The application header: the menu button and brand on the left, the current
 * module next to them, and the active project and signed-in user on the right.
 * It spans the full width and sits above the sidebar, so the project shown here
 * reads as scoping the whole app rather than one section.
 *
 * The horizontal padding is 24px, and the menu button pulls 8px back into it so
 * its 40px hit area is optically flush with the edge rather than indented.
 */
export function TopBar() {
  const { isVisible, menuId, menuButtonRef, toggleFromButton } = useSidebar();
  const { pathname } = useLocation();
  const moduleTitle = moduleTitleFor(pathname);
  const action = isVisible ? 'Ocultar menú' : 'Mostrar menú';

  return (
    <header className="relative z-50 flex h-16 shrink-0 items-center gap-4 border-b border-ink-100 bg-white px-6">
      <button
        ref={menuButtonRef}
        type="button"
        onClick={(event) => toggleFromButton(event.detail === 0)}
        aria-label={action}
        aria-expanded={isVisible}
        aria-controls={menuId}
        title={`${action} (Ctrl/⌘ B)`}
        className={
          '-ml-2 shrink-0 rounded-lg p-2 text-ink-500 transition-colors hover:bg-ink-50 ' +
          'hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
        }
      >
        <MenuIcon />
      </button>
      <Logo variant="mark" />
      {moduleTitle && (
        <>
          <div className="hidden h-8 w-px bg-ink-100 sm:block" aria-hidden="true" />
          <span className="hidden truncate text-sm font-semibold text-ink-800 sm:block">
            {moduleTitle}
          </span>
        </>
      )}
      <div className="flex-1" />
      <ProjectSwitcher />
      <div className="h-8 w-px bg-ink-100" aria-hidden="true" />
      <UserMenu />
    </header>
  );
}
