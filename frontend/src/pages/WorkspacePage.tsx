import { useState } from 'react';
import { AccountCard } from '../components/dashboard/AccountCard';
import { ActiveProjectCard } from '../components/dashboard/ActiveProjectCard';
import { PlatformSummary } from '../components/dashboard/PlatformSummary';
import { QuickActions } from '../components/dashboard/QuickActions';
import { RecentProjectsList } from '../components/dashboard/RecentProjectsList';
import { NewProjectModal } from '../components/projects/NewProjectModal';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';

/** Landing page for every signed-in user. Admins additionally see the
 * platform-wide summary above the project cards. */
export function WorkspacePage() {
  const { user } = useAuth();
  const { reload } = useActiveProject();
  const [isNewProjectOpen, setIsNewProjectOpen] = useState(false);
  const isAdmin = user?.platformRole === 'ADMIN';

  return (
    <div>
      <header className="mb-6">
        <h1 className="text-2xl font-semibold text-ink-800">Inicio</h1>
        <p className="mt-1 text-sm text-ink-500">
          Resumen de tu actividad y accesos a las áreas principales.
        </p>
      </header>

      {isAdmin && <PlatformSummary />}

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
        <div className="flex flex-col gap-6 lg:col-span-2">
          <ActiveProjectCard onNewProject={() => setIsNewProjectOpen(true)} />
          <RecentProjectsList />
        </div>
        <div className="flex flex-col gap-6">
          <QuickActions onNewProject={() => setIsNewProjectOpen(true)} />
          <AccountCard />
        </div>
      </div>

      <NewProjectModal
        open={isNewProjectOpen}
        onClose={() => setIsNewProjectOpen(false)}
        onCreated={reload}
      />
    </div>
  );
}
