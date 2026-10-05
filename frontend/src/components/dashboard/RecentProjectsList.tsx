import { useNavigate } from 'react-router-dom';
import { Alert } from '../ui/Alert';
import { Card } from '../ui/Card';
import { Spinner } from '../ui/Spinner';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useAuth } from '../../hooks/useAuth';
import { formatDate } from '../../utils/date';

const RECENT_COUNT = 5;

export function RecentProjectsList() {
  const { user } = useAuth();
  const { projects, activeProject, isLoading, error, selectProject } = useActiveProject();
  const navigate = useNavigate();
  const isAdmin = user?.platformRole === 'ADMIN';

  function open(projectId: string) {
    selectProject(projectId);
    navigate('/projects');
  }

  return (
    <Card padded={false}>
      <div className="border-b border-ink-100 px-6 py-4">
        <h2 className="text-lg font-semibold text-ink-800">
          {isAdmin ? 'Proyectos de la plataforma' : 'Proyectos recientes'}
        </h2>
      </div>

      {isLoading ? (
        <div className="flex items-center justify-center py-8">
          <Spinner />
        </div>
      ) : error ? (
        <div className="p-6">
          <Alert tone="error">{error}</Alert>
        </div>
      ) : projects.length === 0 ? (
        <p className="px-6 py-8 text-center text-sm text-ink-500">Todavía no hay proyectos.</p>
      ) : (
        <ul>
          {projects.slice(0, RECENT_COUNT).map((project) => (
            <li key={project.id} className="border-b border-ink-100 last:border-b-0">
              <button
                type="button"
                onClick={() => open(project.id)}
                className={
                  'flex w-full flex-wrap items-center justify-between gap-2 px-6 py-3 text-left ' +
                  'transition-colors hover:bg-ink-50 focus:outline-none focus-visible:ring-2 ' +
                  'focus-visible:ring-inset focus-visible:ring-brand-400 ' +
                  (activeProject?.id === project.id ? 'bg-brand-50' : '')
                }
              >
                <span>
                  <span className="block text-sm font-medium text-ink-800">{project.name}</span>
                  <span className="block text-xs text-ink-500">{project.organizationName}</span>
                </span>
                <span className="text-xs text-ink-500">{formatDate(project.lastModified)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
