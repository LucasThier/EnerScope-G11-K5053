import { Link, useLocation, useMatch } from 'react-router-dom';
import { PROJECT_ROUTE_PATTERN, useActiveProject } from '../../hooks/useActiveProject';
import { useSidebar } from '../../hooks/useSidebar';
import { useTheme } from '../../hooks/useTheme';
import { CloseIcon, MenuIcon } from '../ui/icons';
import { Logo } from '../ui/Logo';
import { breadcrumbsFor } from './navItems';
import { ProjectSwitcher } from './ProjectSwitcher';
import { UserMenu } from './UserMenu';

/**
 * The application header: the menu button and brand on the left, the trail to
 * the current location next to them, and the active project and signed-in user
 * on the right. It spans the full width and sits above the sidebar, so the
 * project shown here reads as scoping the whole app rather than one section.
 *
 * The horizontal padding is 24px, and the menu button pulls 8px back into it so
 * its 44px hit area is optically flush with the edge rather than indented.
 */
export function TopBar() {
  const { isDrawer, isVisible, menuId, menuButtonRef, toggleFromButton } = useSidebar();
  const { resolved } = useTheme();
  const { pathname } = useLocation();
  const routeMatch = useMatch(PROJECT_ROUTE_PATTERN);
  const { projects } = useActiveProject();

  const routeProjectId = routeMatch?.params.projectId ?? null;
  const projectName = projects.find((project) => project.id === routeProjectId)?.name ?? null;
  const crumbs = breadcrumbsFor(pathname, projectName);
  const action = isDrawer
    ? isVisible
      ? 'Cerrar menú'
      : 'Abrir menú'
    : isVisible
      ? 'Ocultar menú'
      : 'Mostrar menú';

  return (
    <header
      className={
        'relative z-50 flex h-14 shrink-0 items-center border-b border-ink-100 bg-surface ' +
        'supports-[backdrop-filter]:bg-surface/92 supports-[backdrop-filter]:backdrop-blur-md lg:h-16'
      }
    >
      <div
        className="flex h-full shrink-0 items-center gap-2 px-4 sm:gap-3 lg:w-60 lg:px-6"
      >
        <button
          ref={menuButtonRef}
          type="button"
          onClick={(event) => toggleFromButton(event.detail === 0)}
          aria-label={action}
          aria-expanded={isVisible}
          aria-controls={menuId}
          title={`${action} (Ctrl/⌘ B)`}
          className={
            '-ml-2 flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-ink-500 ' +
            'transition-colors hover:bg-nav-hover hover:text-ink-700 focus:outline-none ' +
            'focus-visible:ring-2 focus-visible:ring-focus'
          }
        >
          {isDrawer && isVisible ? <CloseIcon /> : <MenuIcon />}
        </button>
        <Logo variant="mark" tone={resolved === 'dark' ? 'light' : 'dark'} />
      </div>

      {crumbs.length > 1 && (
        <nav aria-label="Ubicación" className="hidden min-w-0 flex-1 items-center md:flex lg:pl-6">
          <ol className="flex min-w-0 items-center gap-1.5">
            {crumbs.map((crumb, index) => {
              const isLast = index === crumbs.length - 1;
              return (
                <li key={`${crumb.label}-${index}`} className="flex min-w-0 items-center gap-1.5">
                  {index > 0 && (
                    <span aria-hidden="true" className="shrink-0 text-ink-400">
                      /
                    </span>
                  )}
                  {crumb.to && !isLast ? (
                    <Link
                      to={crumb.to}
                      className={
                        'truncate rounded text-sm text-ink-500 transition-colors hover:text-ink-700 ' +
                        'hover:underline focus:outline-none focus-visible:ring-2 focus-visible:ring-focus'
                      }
                    >
                      {crumb.label}
                    </Link>
                  ) : (
                    <span
                      aria-current={isLast ? 'page' : undefined}
                      title={crumb.label}
                      className={
                        'truncate text-sm ' +
                        (isLast ? 'font-medium text-ink-800' : 'text-ink-500')
                      }
                    >
                      {crumb.label}
                    </span>
                  )}
                </li>
              );
            })}
          </ol>
        </nav>
      )}

      <div className="ml-auto flex shrink-0 items-center gap-1 px-4 sm:gap-2 lg:px-6">
        <ProjectSwitcher />
        <div className="hidden h-8 w-px bg-ink-100 sm:block" aria-hidden="true" />
        <UserMenu />
      </div>
    </header>
  );
}
