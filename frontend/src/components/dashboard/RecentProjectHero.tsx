import { useNavigate } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Button } from '../ui/Button';
import { Card } from '../ui/Card';
import { ArrowRightIcon, PlusIcon } from '../ui/icons';
import { useActiveProject } from '../../hooks/useActiveProject';
import type { ProjectSummary } from '../../types/project';
import { formatDateShort } from '../../utils/date';
import { hasReliableRecency, sortByRecency } from '../../utils/recentProjects';

interface RecentProjectHeroProps {
  canCreateProject: boolean;
  onNewProject: () => void;
}

export function RecentProjectHero({ canCreateProject, onNewProject }: RecentProjectHeroProps) {
  const { projects, isLoading, error, selectProject, reload } = useActiveProject();
  const navigate = useNavigate();

  if (isLoading) {
    return <HeroSkeleton />;
  }

  if (error) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Proyecto reciente</h2>
        <Alert tone="error" className="mt-4">
          {error}
        </Alert>
        <Button variant="secondary" size="compact" className="mt-4" onClick={() => void reload()}>
          Reintentar
        </Button>
      </Card>
    );
  }

  if (projects.length === 0) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Proyecto reciente</h2>
        <p className="mt-2 text-sm text-ink-500">
          {canCreateProject
            ? 'Todavía no tenés proyectos accesibles. Creá el primero para empezar a trabajar.'
            : 'Todavía no tenés proyectos accesibles. Cuando te sumen a uno, va a aparecer acá.'}
        </p>
        {canCreateProject && (
          <Button className="mt-4" onClick={onNewProject}>
            <PlusIcon className="h-4 w-4" />
            Nuevo proyecto
          </Button>
        )}
      </Card>
    );
  }

  if (!hasReliableRecency(projects)) {
    return (
      <Card>
        <h2 className="text-lg font-semibold text-ink-800">Proyecto reciente</h2>
        <p className="mt-2 text-sm text-ink-500">
          Los proyectos no traen fecha de modificación, así que no se puede saber cuál es el más
          reciente. Elegí uno desde la lista de proyectos.
        </p>
      </Card>
    );
  }

  const project = sortByRecency(projects)[0];

  function openProject(target: ProjectSummary) {
    selectProject(target.id);
    navigate(`/projects/${target.id}/versions`);
  }

  return (
    <section
      aria-label="Proyecto reciente"
      className={`flex h-full flex-col justify-center gap-3 rounded-card bg-spotlight bg-[linear-gradient(160deg,var(--color-spotlight),var(--color-spotlight-end))] sm:bg-[linear-gradient(135deg,var(--color-spotlight),var(--color-spotlight-end))] p-6 text-white shadow-raise lg:px-8 lg:py-7 [@media(max-height:760px)]:lg:py-4`}
    >
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between sm:gap-6">
        <div className="min-w-0">
          <p className="text-xs font-semibold uppercase tracking-[0.12em] text-spotlight-text">
            PROYECTO RECIENTE
          </p>
          <h2 className="mt-1 truncate text-3xl font-bold leading-tight" title={project.name}>
            {project.name}
          </h2>
        </div>
        <Button
          variant="on-dark"
          className="w-full shrink-0 sm:w-auto"
          onClick={() => openProject(project)}
          aria-label={`Continuar proyecto ${project.name}`}
        >
          Continuar proyecto
          <ArrowRightIcon className="h-4 w-4" />
        </Button>
      </div>
      <dl className="flex flex-col gap-1 text-sm sm:flex-row sm:flex-wrap sm:gap-x-0">
        <div className="flex min-w-0 items-baseline gap-2 sm:pr-4">
          <dt className="text-spotlight-text">Organización</dt>
          <dd className="min-w-0 truncate font-semibold">{project.organizationName || 'Sin organización'}</dd>
        </div>
        <div className="flex items-baseline gap-2 sm:border-l sm:border-spotlight-text/30 sm:pl-4">
          <dt className="text-spotlight-text">Última modificación</dt>
          <dd className="font-semibold">{formatDateShort(project.lastModified) ?? 'Sin fecha registrada'}</dd>
        </div>
      </dl>
    </section>
  );
}

function HeroSkeleton() {
  const bar = 'rounded-control bg-spotlight-text/20 motion-safe:animate-pulse';
  return (
    <div
      role="status"
      aria-live="polite"
      className={`min-h-36 h-full rounded-card bg-spotlight bg-[linear-gradient(160deg,var(--color-spotlight),var(--color-spotlight-end))] sm:bg-[linear-gradient(135deg,var(--color-spotlight),var(--color-spotlight-end))] p-6 shadow-raise sm:p-8`}
    >
      <span className="sr-only">Cargando proyecto reciente</span>
      <div className={`h-3 w-32 ${bar}`} />
      <div className={`mt-4 h-9 w-2/3 max-w-md ${bar}`} />
      <div className={`mt-6 h-10 w-64 max-w-full ${bar}`} />
      <div className={`mt-6 h-control w-48 max-w-full ${bar}`} />
    </div>
  );
}
