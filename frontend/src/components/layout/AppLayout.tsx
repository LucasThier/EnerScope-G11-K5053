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
      <div className="flex min-h-screen flex-col bg-ink-50">
        <TopBar />
        <div className="flex min-h-0 flex-1">
          <Sidebar />
          <main className="min-w-0 flex-1 px-6 py-8">
            <div className="mx-auto w-full max-w-5xl">
              <Outlet />
            </div>
          </main>
        </div>
      </div>
    </ActiveProjectProvider>
  );
}
