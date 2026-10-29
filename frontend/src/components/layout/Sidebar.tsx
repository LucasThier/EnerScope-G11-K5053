import { useEffect } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useActiveProject } from '../../hooks/useActiveProject';
import { useAuth } from '../../hooks/useAuth';
import { useOwnedOrganizations } from '../../hooks/useOwnedOrganizations';
import { useSidebar } from '../../hooks/useSidebar';
import { ADMIN_ITEMS, NAV_ITEMS, type NavItem } from './navItems';
import { LockIcon, PinIcon } from '../ui/icons';

const FOCUSABLE = 'a[href], button:not(:disabled), [tabindex]:not([tabindex="-1"])';

/** Shared row geometry. */
const itemBase = 'flex items-center gap-3 rounded-lg px-3 py-2 text-sm transition-colors';


export function Sidebar() {
  const { user } = useAuth();
  const { activeProject } = useActiveProject();
  const {
    isVisible,
    pushes,
    isDrawer,
    isPinned,
    origin,
    menuId,
    panelRef,
    menuButtonRef,
    keepOpen,
    close,
    togglePinned,
  } = useSidebar();

  const showAdmin = user?.platformRole === 'ADMIN';
  const { owned } = useOwnedOrganizations();
  const items = NAV_ITEMS.map((item) => {
    if (item.key === 'team' && owned.length > 0) return { ...item, to: '/organizations/' + owned[0].id };
    if (item.key === 'configuration' || item.key === 'simulation') {
      const module = item.key === 'configuration' ? 'scenario-configurations' : 'simulations';
      return { ...item, to: activeProject ? '/projects/' + activeProject.id + '/' + module : '/projects' };
    }
    return item;
  });

  useEffect(() => {
    if (!isVisible || (origin !== 'keyboard' && !isDrawer)) {
      return;
    }
    panelRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
  }, [isVisible, origin, isDrawer, panelRef]);

  useEffect(() => {
    if (isVisible) {
      return;
    }
    if (panelRef.current?.contains(document.activeElement)) {
      menuButtonRef.current?.focus();
    }
  }, [isVisible, panelRef, menuButtonRef]);

  useEffect(() => {
    const panel = panelRef.current;
    if (!isDrawer || !isVisible || !panel) {
      return;
    }
    function onKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Tab' || !panel) {
        return;
      }
      const focusables = Array.from(panel.querySelectorAll<HTMLElement>(FOCUSABLE));
      if (focusables.length === 0) {
        return;
      }
      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const active = document.activeElement;
      if (!panel.contains(active)) {
        event.preventDefault();
        (event.shiftKey ? last : first).focus();
        return;
      }
      if (event.shiftKey && active === first) {
        event.preventDefault();
        last.focus();
        return;
      }
      if (!event.shiftKey && active === last) {
        event.preventDefault();
        first.focus();
      }
    }
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [isDrawer, isVisible, panelRef]);

  return (
    <aside
      ref={panelRef}
      id={menuId}
      inert={!isVisible}
      onFocus={keepOpen}
      className={
        'fixed bottom-0 left-0 top-16 z-40 flex w-72 flex-col border-r border-ink-100 bg-surface ' +
        'transition-[transform,box-shadow] duration-200 ease-out motion-reduce:transition-none ' +
        (isVisible ? 'translate-x-0 ' : '-translate-x-full ') +
        (isVisible && !pushes ? 'shadow-2xl' : 'shadow-none')
      }
    >
      <nav aria-label="Secciones" className="flex flex-1 flex-col gap-1 overflow-y-auto p-3">
        {items.map((item) => (
            <NavItemLink key={item.label} item={item} onNavigate={close} />
        ))}

        {showAdmin && (
          <>
            <hr className="my-3 border-ink-100" />
            <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-ink-500">
              Administración
            </p>
            {ADMIN_ITEMS.map((item) => (
              <NavItemLink key={item.label} item={item} onNavigate={close} />
            ))}
          </>
        )}
      </nav>

      <div className="hidden p-3 lg:block">
        <button
          type="button"
          onClick={togglePinned}
          aria-pressed={isPinned}
          title={
            isPinned
              ? 'El menú queda abierto en todas las pantallas'
              : 'Mantener el menú abierto en todas las pantallas'
          }
          className={
            `${itemBase} w-full border font-medium ` +
            'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus ' +
            (isPinned
              ? 'border-ink-200 bg-ink-100 text-ink-700'
              : 'border-ink-100 text-ink-500 hover:bg-ink-50 hover:text-ink-700')
          }
        >
          <span className="shrink-0">
            <PinIcon />
          </span>
          <span>Fijar menú</span>
        </button>
      </div>
    </aside>
  );
}

function NavItemLink({ item, onNavigate }: { item: NavItem; onNavigate: () => void }) {
  const location = useLocation();
  const label = <span className="truncate">{item.label}</span>;

  /* Locked sections keep full-contrast text (ink-500, 5.69:1) and signal their
   * state through the padlock and a lighter weight instead — dimming the label
   * to carry that meaning is what made them unreadable before. */
  if (!item.to) {
    return (
      <span
        aria-disabled="true"
        title={`${item.label} — no disponible`}
        className={`${itemBase} cursor-not-allowed font-normal text-ink-500`}
      >
        <span className="relative shrink-0">{item.icon}</span>
        {label}
        <LockIcon className="ml-auto h-3.5 w-3.5 shrink-0 text-ink-400" />
      </span>
    );
  }

  if (item.matchPattern) {
    const isActive =
      item.matchPattern.test(location.pathname) || (item.key !== 'configuration' && item.key !== 'simulation' && location.pathname === item.to);
    return (
      <Link
        to={item.to}
        onClick={onNavigate}
        aria-current={isActive ? 'page' : undefined}
        className={
          `${itemBase} ` +
          (isActive
            ? 'bg-brand-50 font-semibold text-brand-800'
            : 'font-medium text-ink-500 hover:bg-ink-50 hover:text-ink-700')
        }
      >
        <span className="shrink-0">{item.icon}</span>
        {label}
      </Link>
    );
  }


  return (
    <NavLink
      to={item.to}
      onClick={onNavigate}
      className={({ isActive }) =>
        `${itemBase} ` +
        (isActive
          ? 'bg-brand-50 font-semibold text-brand-800'
          : 'font-medium text-ink-500 hover:bg-ink-50 hover:text-ink-700')
      }
    >
      <span className="shrink-0">{item.icon}</span>
      {label}
    </NavLink>
  );
}
