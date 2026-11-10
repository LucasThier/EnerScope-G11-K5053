import { Outlet, useLocation, useMatch } from 'react-router-dom';
import { ActiveProjectProvider } from '../../hooks/ActiveProjectProvider';
import { MyOrganizationsProvider } from '../../hooks/MyOrganizationsProvider';
import { SidebarProvider } from '../../hooks/SidebarProvider';
import { useActiveProject, PROJECT_ROUTE_PATTERN } from '../../hooks/useActiveProject';
import { useDocumentTitle } from '../../hooks/useDocumentTitle';
import { useOwnedOrganizations } from '../../hooks/useOwnedOrganizations';
import { useSidebar } from '../../hooks/useSidebar';
import { documentSectionFor } from './navItems';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';

/**
 * Signed-in shell: a full-width top bar above a collapsible sidebar and the page
 * content. The active-project state is provided here rather than at the router
 * root so the projects are only fetched once past the auth guard.
 */
export function AppLayout() {
  return (
    <MyOrganizationsProvider>
      <ActiveProjectProvider>
        <SidebarProvider>
          <AppShell />
        </SidebarProvider>
      </ActiveProjectProvider>
    </MyOrganizationsProvider>
  );
}

function AppShell() {
  const { isDrawer, isVisible, pushes } = useSidebar();
  useRouteDocumentTitle();

  return (
    /* h-screen (not min-h-screen) so full-bleed pages like the editor can
       fill the remaining height; padded pages scroll inside PaddedMain. */
    <div className="flex h-screen flex-col overflow-hidden bg-page">
      <a
        href="#contenido"
        className={
          'sr-only z-[60] focus:not-sr-only focus:absolute focus:left-4 focus:top-3 focus:rounded-lg ' +
          'focus:bg-raised focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-ink-800 ' +
          'focus:shadow-lg focus:outline-none focus:ring-2 focus:ring-focus'
        }
      >
        Saltar al contenido
      </a>
      <TopBar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <div
          aria-hidden="true"
          className={
            'shrink-0 transition-[width] duration-200 ease-out motion-reduce:transition-none ' +
            (pushes ? 'w-60' : 'w-0')
          }
        />
        {/* Pages control their own area: PaddedMain centres regular pages,
            while full-bleed pages (the editor) fill the space themselves. */}
        <div
          id="contenido"
          tabIndex={-1}
          className="flex min-h-0 min-w-0 flex-1 flex-col focus:outline-none"
        >
          <Outlet />
        </div>
        <div
          aria-hidden="true"
          className={
            'fixed inset-x-0 bottom-0 top-14 z-30 touch-none bg-scrim/40 transition-opacity ' +
            'duration-200 motion-reduce:transition-none ' +
            (isDrawer && isVisible ? 'opacity-100' : 'pointer-events-none opacity-0')
          }
        />
      </div>
    </div>
  );
}

function useRouteDocumentTitle() {
  const { pathname } = useLocation();
  const { projects } = useActiveProject();
  const { owned } = useOwnedOrganizations();
  const projectMatch = useMatch(PROJECT_ROUTE_PATTERN);
  const organizationMatch = useMatch('/organizations/:organizationId/*');

  const projectName =
    projects.find((project) => project.id === projectMatch?.params.projectId)?.name ?? null;
  const organizationName =
    owned.find(
      (organization) => organization.id === organizationMatch?.params.organizationId,
    )?.name ?? null;

  useDocumentTitle(documentSectionFor(pathname, projectName, organizationName));
}
