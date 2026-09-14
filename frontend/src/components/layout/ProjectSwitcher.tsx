import { useActiveProject } from '../../hooks/useActiveProject';
import { useDismissable } from '../../hooks/useDismissable';
import { ChevronDownIcon } from '../ui/icons';

/**
 * Picks the project the app is working on. It renders a plain label instead of
 * a menu whenever there is nothing to switch to — while the list loads, when it
 * failed, or when the account has a single project — so the chevron only ever
 * appears next to a menu that actually opens.
 *
 * The trigger is set in ink, not brand green: it names the current context
 * rather than offering an action, and reserving green for actions and the
 * active state keeps the top bar quiet. Green does mark the selected row inside
 * the menu, where it genuinely means "this one".
 */
export function ProjectSwitcher() {
  const { projects, activeProject, isLoading, error, selectProject } = useActiveProject();
  const { isOpen, ref, close, toggle } = useDismissable<HTMLDivElement>();

  if (isLoading) {
    return <span className="text-sm text-ink-500">Cargando proyectos…</span>;
  }
  if (error) {
    return <span className="text-sm text-ink-500">Proyectos no disponibles</span>;
  }
  if (!activeProject) {
    return <span className="text-sm text-ink-500">Sin proyectos</span>;
  }
  if (projects.length === 1) {
    return <span className="text-sm font-semibold text-ink-800">{activeProject.name}</span>;
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={toggle}
        aria-haspopup="listbox"
        aria-expanded={isOpen}
        className={
          'flex items-center gap-2 rounded-lg px-3 py-2 text-sm font-semibold text-ink-800 ' +
          'transition-colors hover:bg-ink-50 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
        }
      >
        {activeProject.name}
        <ChevronDownIcon
          className={`h-4 w-4 text-ink-400 transition-transform ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <ul
          role="listbox"
          className={
            'absolute right-0 z-20 mt-1 max-h-80 w-72 overflow-y-auto rounded-xl border ' +
            'border-ink-100 bg-white py-1 shadow-lg'
          }
        >
          {projects.map((project) => {
            const isActive = project.id === activeProject.id;
            return (
              <li key={project.id} role="option" aria-selected={isActive}>
                <button
                  type="button"
                  onClick={() => {
                    selectProject(project.id);
                    close();
                  }}
                  className={
                    'block w-full px-3 py-2 text-left transition-colors ' +
                    (isActive ? 'bg-brand-50' : 'hover:bg-ink-50')
                  }
                >
                  <span
                    className={
                      'block truncate text-sm ' +
                      (isActive ? 'font-semibold text-brand-800' : 'text-ink-700')
                    }
                  >
                    {project.name}
                  </span>
                  <span className="block truncate text-xs text-ink-500">
                    {project.organizationName}
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
