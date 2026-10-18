import { useEffect, useId, useMemo } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import { useAuth } from '../../hooks/useAuth';
import { useOwnedOrganizations } from '../../hooks/useOwnedOrganizations';
import { useSidebar } from '../../hooks/useSidebar';
import { NAV_GROUPS, type NavGroup, type NavItem } from './navItems';
import { LockIcon } from '../ui/icons';

const FOCUSABLE = 'a[href], button:not(:disabled), [tabindex]:not([tabindex="-1"])';

/** Shared row geometry. */
const itemBase =
  'relative flex h-11 items-center gap-3 rounded-xl px-3 text-sm transition-colors ' +
  'focus:outline-none focus-visible:ring-2 focus-visible:ring-focus';

export function Sidebar() {
  const { user } = useAuth();
  const { origin, isVisible, pushes, isDrawer, menuId, panelRef, menuButtonRef, close, keepOpen } =
    useSidebar();

  const showAdmin = user?.platformRole === 'ADMIN';
  const { owned } = useOwnedOrganizations();
  const ownedId = owned[0]?.id;

  const groups = useMemo(() => visibleGroups(showAdmin, ownedId), [showAdmin, ownedId]);

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
        'fixed bottom-0 left-0 z-40 flex flex-col border-r border-ink-100 bg-nav ' +
        'transition-[transform,box-shadow] duration-200 ease-out motion-reduce:transition-none ' +
        (isDrawer ? 'top-14 w-[17rem] max-w-[85vw] ' : 'top-16 w-60 ') +
        (isVisible ? 'translate-x-0 ' : '-translate-x-full ') +
        (isVisible && !pushes ? 'shadow-2xl' : 'shadow-none')
      }
    >
      <nav
        aria-label="Secciones"
        className="flex-1 overflow-y-auto overscroll-contain px-3 py-3 pb-[max(0.75rem,env(safe-area-inset-bottom))]"
      >
        {groups.map((group, index) => (
          <NavGroupBlock
            key={group.title}
            group={group}
            isFirst={index === 0}
            onNavigate={close}
          />
        ))}
      </nav>
    </aside>
  );
}

function visibleGroups(showAdmin: boolean, ownedId: string | undefined): NavGroup[] {
  return NAV_GROUPS.map((group) => ({
    ...group,
    items: group.items
      .filter((item) => !item.adminOnly || showAdmin)
      .map((item) =>
        item.key === 'team' && ownedId ? { ...item, to: `/organizations/${ownedId}` } : item,
      ),
  })).filter((group) => group.items.length > 0);
}

interface GroupProps {
  group: NavGroup;
  isFirst: boolean;
  onNavigate: () => void;
}

function NavGroupBlock({ group, isFirst, onNavigate }: GroupProps) {
  const titleId = useId();

  return (
    <div className={isFirst ? '' : 'mt-5'}>
      <p
        id={titleId}
        className="px-3 pb-2 text-[11px] font-semibold uppercase tracking-[0.08em] text-ink-500"
      >
        {group.title}
      </p>
      <ul aria-labelledby={titleId} className="flex flex-col gap-1">
        {group.items.map((item) => (
          <li key={item.label}>
            <NavItemRow item={item} onNavigate={onNavigate} />
          </li>
        ))}
      </ul>
    </div>
  );
}

const restingTone = 'font-medium text-ink-500 hover:bg-nav-hover hover:text-ink-700';
const activeTone = 'bg-nav-active font-semibold text-brand-900';

function NavItemRow({ item, onNavigate }: { item: NavItem; onNavigate: () => void }) {
  const location = useLocation();
  const fullName = item.fullLabel ?? item.label;

  const body = (
    <>
      <span className="shrink-0">{item.icon}</span>
      <span className="truncate">{item.label}</span>
    </>
  );

  /* Locked sections keep full-contrast text (ink-500, 5.69:1) and signal their
   * state through the padlock and a lighter weight instead — dimming the label
   * to carry that meaning is what made them unreadable before. */
  if (!item.to) {
    const isSoon = item.availability === 'soon';
    return (
      <span
        aria-disabled="true"
        title={isSoon ? `${fullName} — próximamente` : fullName}
        className={`${itemBase} cursor-not-allowed font-normal text-ink-500`}
      >
        <span className="shrink-0 text-ink-400">{item.icon}</span>
        <span className="truncate">{item.label}</span>
        <span className="sr-only">{isSoon ? '— próximamente' : '— sin permisos'}</span>
        {!isSoon && <LockIcon className="ml-auto h-3.5 w-3.5 shrink-0 text-ink-500" />}
      </span>
    );
  }

  const marker = (
    <span
      aria-hidden="true"
      className="absolute left-0 top-1/2 h-6 w-[3px] -translate-y-1/2 rounded-r-full bg-brand-900"
    />
  );

  const linkProps = {
    to: item.to,
    onClick: onNavigate,
    'aria-label': item.fullLabel,
    title: item.fullLabel,
  };

  if (item.matchPattern) {
    const isActive = item.matchPattern.test(location.pathname) || location.pathname === item.to;
    return (
      <Link
        {...linkProps}
        aria-current={isActive ? 'page' : undefined}
        className={`${itemBase} ${isActive ? activeTone : restingTone}`}
      >
        {isActive && marker}
        {body}
      </Link>
    );
  }

  return (
    <NavLink
      {...linkProps}
      className={({ isActive }) => `${itemBase} ${isActive ? activeTone : restingTone}`}
    >
      {({ isActive }) => (
        <>
          {isActive && marker}
          {body}
        </>
      )}
    </NavLink>
  );
}
