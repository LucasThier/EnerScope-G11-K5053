import { useMemo, useState } from 'react';
import { PageHeader } from '../components/ui/PageHeader';
import { ProjectsCounter } from '../components/dashboard/ProjectsCounter';
import { QuickActions } from '../components/dashboard/QuickActions';
import { RecentProjectHero } from '../components/dashboard/RecentProjectHero';
import { RecentProjectsList } from '../components/dashboard/RecentProjectsList';
import { ScenariosPanel } from '../components/dashboard/ScenariosPanel';
import { NewProjectModal } from '../components/projects/NewProjectModal';
import { useActiveProject } from '../hooks/useActiveProject';
import { useAuth } from '../hooks/useAuth';
import { useMyOrganizations } from '../hooks/useMyOrganizations';
import { useNavigationTree } from '../hooks/useNavigationTree';
import { isPlatformAdmin } from '../permissions/access';
import { quickActionsFor } from '../utils/homeScenarios';
import { mostRecentProject } from '../utils/recentProjects';

/**
 * Landing page for every signed-in user. From `lg` it is a 12-column grid that fits a 1280×720 screen without
 * scrolling: the recent project and the project counter on top, the scenarios of that project on the left below,
 * and the recent projects and the quick access chips on the right. Below `lg` the same blocks stack in the order
 * of the DOM: header, hero, counter, scenarios, recent projects, quick access.
 */
export function WorkspacePage() {
  const { user } = useAuth();
  const { projects, isLoading, selectProject, reload } = useActiveProject();
  const { organizations, loading: organizationsLoading } = useMyOrganizations();
  const groups = useNavigationTree();
  const [isNewProjectOpen, setIsNewProjectOpen] = useState(false);
  const isAdmin = isPlatformAdmin(user);
  const canCreateProject = !organizationsLoading && organizations.length > 0;
  const openNewProject = () => setIsNewProjectOpen(true);

  const recent = useMemo(() => (isLoading ? null : mostRecentProject(projects)), [projects, isLoading]);
  const actions = useMemo(
    () => quickActionsFor(groups, { isAdmin, canCreateProject, compareProjectId: recent?.id ?? null }),
    [groups, isAdmin, canCreateProject, recent],
  );
  const canCompare = actions.some((action) => action.id === 'compare');

  return (
    <div className="grid grid-cols-1 gap-6 lg:grid-cols-12 [@media(max-height:760px)]:lg:gap-5">
      <PageHeader
        spaced={false}
        className="lg:col-span-12"
        title="Tu espacio de trabajo"
        description="Accesos directos a la actividad reciente."
      />

      <div className="min-w-0 lg:col-span-8">
        <RecentProjectHero canCreateProject={canCreateProject} onNewProject={openNewProject} />
      </div>
      <div className="min-w-0 lg:col-span-4">
        <ProjectsCounter />
      </div>

      {recent && (
        <div className="min-w-0 lg:col-span-7 lg:self-start">
          <ScenariosPanel project={recent} canCompare={canCompare} />
        </div>
      )}
      <div className={`flex min-w-0 flex-col gap-6 [@media(max-height:760px)]:lg:gap-5 ${recent ? 'lg:col-span-5' : 'lg:col-span-12'}`}>
        <RecentProjectsList />
        <QuickActions
          actions={actions}
          onNewProject={openNewProject}
          onCompare={recent ? () => selectProject(recent.id) : undefined}
        />
      </div>

      {isNewProjectOpen && (
        <NewProjectModal open onClose={() => setIsNewProjectOpen(false)} onCreated={reload} />
      )}
    </div>
  );
}
