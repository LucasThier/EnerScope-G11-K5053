import { useEffect, useId, useRef, type KeyboardEvent } from 'react';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useDismissable } from '../../hooks/useDismissable';
import { CheckIcon, ChevronDownIcon, FolderIcon } from '../ui/icons';

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
  const menuId = useId();
  const triggerRef = useRef<HTMLButtonElement>(null);
  const itemRefs = useRef<(HTMLButtonElement | null)[]>([]);

  useEffect(() => {
    if (isOpen) {
      const current = projects.findIndex((project) => project.id === activeProject?.id);
      itemRefs.current[current < 0 ? 0 : current]?.focus();
    }
  }, [isOpen, projects, activeProject]);

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
    return (
      <span className="flex h-10 max-w-[7.5rem] items-center gap-2 px-1 sm:max-w-[10rem] lg:max-w-[16rem]">
        <FolderIcon className="h-4 w-4 shrink-0 text-ink-500" />
        <span className="truncate text-sm font-medium text-ink-800" title={activeProject.name}>
          {activeProject.name}
        </span>
      </span>
    );
  }

  function focusItem(index: number) {
    const items = itemRefs.current;
    const count = items.length;
    items[((index % count) + count) % count]?.focus();
  }

  function handleMenuKeyDown(event: KeyboardEvent<HTMLUListElement>) {
    const current = itemRefs.current.indexOf(document.activeElement as HTMLButtonElement);
    switch (event.key) {
      case 'ArrowDown':
        event.preventDefault();
        focusItem(current + 1);
        break;
      case 'ArrowUp':
        event.preventDefault();
        focusItem(current - 1);
        break;
      case 'Home':
        event.preventDefault();
        focusItem(0);
        break;
      case 'End':
        event.preventDefault();
        focusItem(itemRefs.current.length - 1);
        break;
      case 'Escape':
        event.preventDefault();
        close();
        triggerRef.current?.focus();
        break;
      case 'Tab':
        close();
        break;
      default:
        break;
    }
  }

  return (
    <div ref={ref} className="relative">
      <button
        ref={triggerRef}
        type="button"
        onClick={toggle}
        aria-haspopup="menu"
        aria-expanded={isOpen}
        aria-controls={isOpen ? menuId : undefined}
        aria-label={`Proyecto activo: ${activeProject.name}`}
        title={activeProject.name}
        className={
          'flex h-control-compact max-w-[7.5rem] items-center gap-2 rounded-control border border-control-border bg-nav px-2.5 ' +
          'transition-colors hover:bg-nav-hover focus:outline-none focus-visible:ring-2 ' +
          'focus-visible:ring-focus sm:max-w-[10rem] lg:max-w-[16rem]'
        }
      >
        <FolderIcon className="h-4 w-4 shrink-0 text-ink-500" />
        <span className="truncate text-sm font-medium text-ink-800">{activeProject.name}</span>
        <ChevronDownIcon
          className={`h-4 w-4 shrink-0 text-ink-500 transition-transform ${isOpen ? 'rotate-180' : ''}`}
        />
      </button>

      {isOpen && (
        <ul
          id={menuId}
          role="menu"
          aria-label="Cambiar de proyecto"
          onKeyDown={handleMenuKeyDown}
          className={
            'absolute right-0 z-20 mt-2 max-h-80 w-72 max-w-[calc(100vw-2rem)] overflow-y-auto ' +
            'rounded-xl border border-ink-100 bg-raised py-1 shadow-float'
          }
        >
          {projects.map((project, index) => {
            const isActive = project.id === activeProject.id;
            return (
              <li key={project.id}>
                <button
                  ref={(el) => {
                    itemRefs.current[index] = el;
                  }}
                  type="button"
                  role="menuitemradio"
                  aria-checked={isActive}
                  onClick={() => {
                    selectProject(project.id);
                    close();
                    triggerRef.current?.focus();
                  }}
                  className={
                    'flex w-full items-center gap-2 px-3 py-2 text-left transition-colors ' +
                    'focus:outline-none focus-visible:ring-2 focus-visible:ring-inset ' +
                    'focus-visible:ring-focus ' +
                    (isActive ? 'bg-nav-active' : 'hover:bg-nav-hover')
                  }
                >
                  <span className="min-w-0 flex-1">
                    <span
                      className={
                        'block truncate text-sm ' +
                        (isActive ? 'font-semibold text-brand-900' : 'text-ink-700')
                      }
                    >
                      {project.name}
                    </span>
                    <span className="block truncate text-xs text-ink-500">
                      {project.organizationName}
                    </span>
                  </span>
                  {isActive && <CheckIcon className="h-4 w-4 shrink-0 text-brand-900" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
