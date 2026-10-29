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
      className={`rounded-card bg-spotlight bg-[linear-gradient(160deg,var(--color-spotlight),var(--color-spotlight-end))] sm:bg-[linear-gradient(135deg,var(--color-spotlight),var(--color-spotlight-end))] p-6 text-white shadow-raise sm:p-8`}
    >
      <p className="text-xs font-semibold uppercase tracking-[0.12em] text-spotlight-text">
        PROYECTO RECIENTE
      </p>
      <h2
        className="mt-2 break-words text-3xl font-bold leading-tight sm:text-4xl"
        title={project.name}
      >
        {project.name}
      </h2>
      <dl className="mt-5 flex flex-col gap-4 sm:flex-row sm:gap-0">
        <div className="min-w-0 sm:pr-6">
          <dt className="text-sm text-spotlight-text">Organización</dt>
          <dd className="mt-0.5 break-words text-base font-semibold">
            {project.organizationName || 'Sin organización'}
          </dd>
        </div>
        <div className="min-w-0 sm:border-l sm:border-spotlight-text/30 sm:pl-6">
          <dt className="text-sm text-spotlight-text">Última modificación</dt>
          <dd className="mt-0.5 text-base font-semibold">
            {formatDateShort(project.lastModified) ?? 'Sin fecha registrada'}
          </dd>
        </div>
      </dl>
      <Button
        variant="on-dark"
        className="mt-6 w-full sm:w-auto"
        onClick={() => openProject(project)}
        aria-label={`Continuar proyecto ${project.name}`}
      >
        Continuar proyecto
        <ArrowRightIcon className="h-4 w-4" />
      </Button>
    </section>
  );
}

function HeroSkeleton() {
  const bar = 'rounded-control bg-spotlight-text/20 motion-safe:animate-pulse';
  return (
    <div
      role="status"
      aria-live="polite"
      className={`min-h-60 rounded-card bg-spotlight bg-[linear-gradient(160deg,var(--color-spotlight),var(--color-spotlight-end))] sm:bg-[linear-gradient(135deg,var(--color-spotlight),var(--color-spotlight-end))] p-6 shadow-raise sm:p-8`}
    >
      <span className="sr-only">Cargando proyecto reciente</span>
      <div className={`h-3 w-32 ${bar}`} />
      <div className={`mt-4 h-9 w-2/3 max-w-md ${bar}`} />
      <div className={`mt-6 h-10 w-64 max-w-full ${bar}`} />
      <div className={`mt-6 h-control w-48 max-w-full ${bar}`} />
    </div>
  );
}
