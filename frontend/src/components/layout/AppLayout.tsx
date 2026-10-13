import { Outlet } from 'react-router-dom';
import { ActiveProjectProvider } from '../../hooks/ActiveProjectProvider';
import { SidebarProvider } from '../../hooks/SidebarProvider';
import { useSidebar } from '../../hooks/useSidebar';
import { Sidebar } from './Sidebar';
import { TopBar } from './TopBar';

/**
 * Signed-in shell: a full-width top bar above a collapsible sidebar and the page
 * content. The active-project state is provided here rather than at the router
 * root so the projects are only fetched once past the auth guard.
 */
export function AppLayout() {
  return (
    <ActiveProjectProvider>
      <SidebarProvider>
        <AppShell />
      </SidebarProvider>
    </ActiveProjectProvider>
  );
}

function AppShell() {
  const { pushes, isDrawer, isVisible } = useSidebar();

  return (
    /* h-screen (not min-h-screen) so full-bleed pages like the editor can
       fill the remaining height; padded pages scroll inside PaddedMain. */
    <div className="flex h-screen flex-col overflow-hidden bg-ink-50">
      <TopBar />
      <div className="flex min-h-0 flex-1">
        <Sidebar />
        <div
          aria-hidden="true"
          className={
            'shrink-0 transition-[width] duration-200 ease-out motion-reduce:transition-none ' +
            (pushes ? 'w-72' : 'w-0')
          }
        />
        {/* Pages control their own area: PaddedMain centres regular pages,
            while full-bleed pages (the editor) fill the space themselves. */}
        <Outlet />
        <div
          aria-hidden="true"
          className={
            'fixed inset-x-0 bottom-0 top-16 z-30 bg-ink-800/40 transition-opacity duration-200 ' +
            'motion-reduce:transition-none ' +
            (isDrawer && isVisible ? 'opacity-100' : 'pointer-events-none opacity-0')
          }
        />
      </div>
    </div>
  );
}
