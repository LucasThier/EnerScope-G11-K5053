import { useState } from 'react';
import { PlatformIndicators } from '../components/dashboard/PlatformIndicators';
import { QuickActions } from '../components/dashboard/QuickActions';
import { RecentProjectHero } from '../components/dashboard/RecentProjectHero';
import { RecentProjectsList } from '../components/dashboard/RecentProjectsList';
import { NewProjectModal } from '../components/projects/NewProjectModal';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import { useMyOrganizations } from '../hooks/useMyOrganizations';

/** Landing page for every signed-in user. Platform admins additionally get the
 * users and organizations indicators. */
export function WorkspacePage() {
  const { user } = useAuth();
  const { reload } = useActiveProject();
  const {
    organizations,
    belongsToOrganization,
    loading: organizationsLoading,
    error: organizationsError,
  } = useMyOrganizations();
  const [isNewProjectOpen, setIsNewProjectOpen] = useState(false);
  const isAdmin = user?.platformRole === 'ADMIN';
  const canCreateProject = !organizationsLoading && organizations.length > 0;
  const openNewProject = () => setIsNewProjectOpen(true);

  return (
    <div className="flex flex-col gap-6">
      <header>
        <h1 className="text-3xl font-bold tracking-tight text-ink-800 sm:text-4xl">
          Tu espacio de trabajo
        </h1>
        <p className="mt-1 text-base text-ink-500">Accesos directos a la actividad reciente.</p>
      </header>

      <RecentProjectHero canCreateProject={canCreateProject} onNewProject={openNewProject} />

      <PlatformIndicators
        isAdmin={isAdmin}
        organizations={organizations}
        organizationsLoading={organizationsLoading}
        organizationsError={organizationsError}
      />

      <div className="grid grid-cols-1 gap-6 lg:grid-cols-5">
        <div className="min-w-0 lg:col-span-3">
          <RecentProjectsList />
        </div>
        <div className="min-w-0 lg:col-span-2">
          <QuickActions
            isAdmin={isAdmin}
            canCreateProject={canCreateProject}
            belongsToOrganization={belongsToOrganization}
            onNewProject={openNewProject}
          />
        </div>
      </div>

      {isNewProjectOpen && (
        <NewProjectModal
          open
          onClose={() => setIsNewProjectOpen(false)}
          onCreated={reload}
        />
      )}
    </div>
  );
}
