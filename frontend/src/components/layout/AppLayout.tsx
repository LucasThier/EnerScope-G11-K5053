import { Outlet } from 'react-router-dom';
import { ActiveProjectProvider } from '../../hooks/ActiveProjectProvider';
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
      {/* h-screen (not min-h-screen) so full-bleed pages like the editor can
          fill the remaining height; padded pages scroll inside PaddedMain. */}
      <div className="flex h-screen flex-col bg-ink-50">
        <TopBar />
        <div className="flex min-h-0 flex-1">
          <Sidebar />
          {/* Pages control their own area: PaddedMain centres regular pages,
              while full-bleed pages (the editor) fill the space themselves. */}
          <Outlet />
        </div>
      </div>
    </ActiveProjectProvider>
  );
}
