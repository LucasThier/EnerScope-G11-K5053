import { useCallback, useEffect, useId, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useNavigationTree } from '../../hooks/useNavigationTree';
import { useSidebar } from '../../hooks/useSidebar';
import { activeLeaf, type NavGroup, type NavItem } from './navItems';
import { ChevronDownIcon, LockIcon } from '../ui/icons';
import { Logo } from '../ui/Logo';

const FOCUSABLE = 'a[href], button:not(:disabled), [tabindex]:not([tabindex="-1"])';

/** Shared row geometry. The rail is green in both themes, so the focus ring is
 * the rail's own white (`sidebar-focus`), not the app-wide deep-green `focus`,
 * which all but disappears on it. */
const itemBase =
  'relative flex h-11 items-center gap-3 rounded-xl px-3 text-sm transition-colors ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-focus';

export function Sidebar() {
  const { isVisible, pushes, isDrawer, isFixed, menuId, panelRef, menuButtonRef, close } =
    useSidebar();
  const { pathname } = useLocation();
  const groups = useNavigationTree();
  const active = useMemo(() => activeLeaf(groups, pathname), [groups, pathname]);
  const [expanded, setExpanded] = useState<Record<string, boolean>>({});

  const toggleModule = useCallback(
    (item: NavItem) => {
      const id = moduleKey(item);
      setExpanded((current) => ({
        ...current,
        [id]: !(current[id] ?? containsActiveChild(item, active)),
      }));
    },
    [active],
  );

  /* Opening the drawer moves focus into it; the fixed column never takes focus
     on its own. */
  useEffect(() => {
    if (!isDrawer || !isVisible) {
      return;
    }
    panelRef.current?.querySelector<HTMLElement>(FOCUSABLE)?.focus();
  }, [isVisible, isDrawer, panelRef]);

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
      className={
        'fixed bottom-0 left-0 flex flex-col border-r border-sidebar-line bg-sidebar ' +
        'transition-[transform,box-shadow] duration-200 ease-out motion-reduce:transition-none ' +
        /* Fixed (>= 80rem), the rail runs the full height and carries the logo,
           over the top bar's empty left cell. As a drawer it opens under the
           top bar, which keeps the logo: 56px tall below lg, 64px from lg. */
        (isFixed ? 'top-0 z-[55] w-60 ' : 'top-14 z-40 w-[17rem] max-w-[85vw] lg:top-16 ') +
        (isVisible ? 'translate-x-0 ' : '-translate-x-full ') +
        (isVisible && !pushes ? 'shadow-2xl' : 'shadow-none')
      }
    >
      {isFixed && (
        /* Same 64px as the top bar (h-16 at lg+, and fixed implies >= 80rem), so the
           cell ends on the bar's bottom line; centred horizontally in the rail.
           The 44px art fills its PNG edge to edge (no transparent margin to
           correct), but centred it read high against the first section title,
           so the link is nudged 6px down: 16px above, 4px below in the cell.
           translate-y leaves layout and focus order alone; the ring moves with it. */
        <div className="flex h-16 shrink-0 items-center justify-center px-3">
          <Link
            to="/app"
            aria-label="EnerScope, ir al inicio"
            className="flex shrink-0 translate-y-1.5 rounded-lg focus:outline-none focus-visible:ring-2 focus-visible:ring-sidebar-focus"
          >
            <Logo variant="mark" size="md" tone="sidebar" />
          </Link>
        </div>
      )}
      <nav
        aria-label="Secciones"
        className="flex-1 overflow-y-auto overscroll-contain px-3 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
      >
        {groups.map((group, index) => (
          <NavGroupBlock
            key={group.title}
            group={group}
            isFirst={index === 0}
            active={active}
            expanded={expanded}
            onToggleModule={toggleModule}
            onNavigate={close}
          />
        ))}
      </nav>
    </aside>
  );
}

function moduleKey(item: NavItem): string {
  return item.id ?? item.label;
}

function containsActiveChild(item: NavItem, active: NavItem | null): boolean {
  return active !== null && (item.children ?? []).includes(active);
}

interface GroupProps {
  group: NavGroup;
  isFirst: boolean;
  active: NavItem | null;
  expanded: Record<string, boolean>;
  onToggleModule: (item: NavItem) => void;
  onNavigate: () => void;
}

function NavGroupBlock({ group, isFirst, active, expanded, onToggleModule, onNavigate }: GroupProps) {
  const titleId = useId();

  return (
    <div className={isFirst ? '' : 'mt-5'}>
      <p
        id={titleId}
        className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-sidebar-muted"
      >
        {group.title}
      </p>
      <ul aria-labelledby={titleId} className="flex flex-col gap-1">
        {group.items.map((item) => (
          <li key={moduleKey(item)}>
            {item.children ? (
              <NavModuleRow
                item={item}
                active={active}
                expandedState={expanded[moduleKey(item)]}
                onToggle={() => onToggleModule(item)}
                onNavigate={onNavigate}
              />
            ) : (
              <NavItemRow item={item} active={active} onNavigate={onNavigate} />
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

const restingTone = 'font-medium text-sidebar-text hover:bg-sidebar-hover';
/* The pill alone marks the current row; the side marker it used to carry is gone. */
const activeTone = 'bg-sidebar-active font-semibold text-sidebar-active-text';
/* A module whose child is the current row: the soft hover fill and semi-bold
 * white, so the lime pill is only ever on one row — the current leaf itself. */
const parentTone = 'bg-sidebar-hover font-semibold text-sidebar-text';

interface ModuleProps {
  item: NavItem;
  active: NavItem | null;
  expandedState: boolean | undefined;
  onToggle: () => void;
  onNavigate: () => void;
}

function NavModuleRow({ item, active, expandedState, onToggle, onNavigate }: ModuleProps) {
  const panelId = useId();
  const containsActive = containsActiveChild(item, active);
  const open = expandedState ?? containsActive;

  return (
    <>
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        aria-controls={panelId}
        aria-label={item.fullLabel}
        title={item.fullLabel ?? item.label}
        className={`${itemBase} w-full text-left ${containsActive ? parentTone : restingTone}`}
      >
        <span className="shrink-0">{item.icon}</span>
        <span className="truncate">{item.label}</span>
        <ChevronDownIcon
          className={
            'ml-auto h-4 w-4 shrink-0 transition-transform duration-150 ease-out ' +
            'motion-reduce:transition-none ' +
            (open ? 'rotate-180' : 'rotate-0')
          }
        />
      </button>
      <ul
        id={panelId}
        className={
          open ? 'mt-1 ml-5 flex flex-col gap-1 border-l border-sidebar-line pl-3' : 'hidden'
        }
      >
        {open &&
          (item.children ?? []).map((child) => (
            <li key={child.label}>
              <NavItemRow item={child} active={active} onNavigate={onNavigate} />
            </li>
          ))}
      </ul>
    </>
  );
}

function NavItemRow({
  item,
  active,
  onNavigate,
}: {
  item: NavItem;
  active: NavItem | null;
  onNavigate: () => void;
}) {
  const fullName = item.fullLabel ?? item.label;

  const body = (
    <>
      {item.icon && <span className="shrink-0">{item.icon}</span>}
      <span className="truncate">{item.label}</span>
    </>
  );

  /* Locked sections keep full-contrast text (the rail's white, 10.75:1, the same
   * as a resting row) and signal their state through the padlock, a dimmer
   * icon (sidebar-icon-muted, 4.98:1) and a lighter weight instead — dimming
   * the label to carry that meaning is what made them unreadable before. */
  if (!item.to) {
    const isSoon = item.availability === 'soon';
    const reason = isSoon ? 'próximamente' : item.reason;
    return (
      <span
        aria-disabled="true"
        title={reason ? `${fullName} — ${reason}` : fullName}
        className={`${itemBase} cursor-not-allowed font-normal text-sidebar-text`}
      >
        <span className="shrink-0 text-sidebar-icon-muted">{item.icon}</span>
        <span className="truncate">{item.label}</span>
        {reason && <span className="sr-only">{`— ${reason}`}</span>}
        {!isSoon && <LockIcon className="ml-auto h-3.5 w-3.5 shrink-0 text-sidebar-icon-muted" />}
      </span>
    );
  }

  const linkProps = {
    to: item.to,
    onClick: onNavigate,
    'aria-label': item.fullLabel,
    title: fullName,
  };

  const isActive = item === active;
  return (
    <Link
      {...linkProps}
      aria-current={isActive ? 'page' : undefined}
      className={`${itemBase} ${isActive ? activeTone : restingTone}`}
    >
      {body}
    </Link>
  );
}
