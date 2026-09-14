import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useLocalPreference } from '../../hooks/useLocalPreference';
import {
  BuildingIcon,
  ChevronsLeftIcon,
  ChevronsRightIcon,
  CompareIcon,
  FlaskIcon,
  FolderIcon,
  HomeIcon,
  LockIcon,
  ReportIcon,
  SettingsIcon,
  TeamIcon,
  UsersIcon,
  ValueChainIcon,
} from '../ui/icons';

interface NavItem {
  label: string;
  icon: ReactNode;
  /** Absent until the section has a page; the entry renders locked instead of linking nowhere. */
  to?: string;
}

const COLLAPSED_KEY = 'sidebarCollapsed';

/**
 * The sections from the product design. The ones still to be built are listed
 * but locked: showing the full map of the product is intentional, linking to
 * pages that do not exist is not.
 */
const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio / Dashboard', icon: <HomeIcon />, to: '/app' },
  { label: 'Proyectos', icon: <FolderIcon />, to: '/projects' },
  { label: 'Mapa de la Cadena de Valor', icon: <ValueChainIcon /> },
  { label: 'Simulaciones / Escenarios', icon: <FlaskIcon /> },
  { label: 'Comparar Escenarios', icon: <CompareIcon /> },
  { label: 'Reportes', icon: <ReportIcon /> },
  { label: 'Organización / Equipo', icon: <TeamIcon /> },
  { label: 'Configuración', icon: <SettingsIcon /> },
];

/** Platform administration, kept separate because it is not part of the project workspace. */
const ADMIN_ITEMS: NavItem[] = [
  { label: 'Usuarios', icon: <UsersIcon />, to: '/admin/users' },
  { label: 'Organizaciones', icon: <BuildingIcon />, to: '/admin/organizations' },
];

/** Shared row geometry. Padding is applied per state so the collapsed rail can
 * centre its icons without inventing a second set of sizes. */
const itemBase = 'flex items-center gap-3 rounded-lg py-2 text-sm transition-colors';

const itemPadding = (isCollapsed: boolean) => (isCollapsed ? 'justify-center px-2' : 'px-3');

export function Sidebar() {
  const { user } = useAuth();
  const [isCollapsed, setIsCollapsed] = useLocalPreference(COLLAPSED_KEY, false);

  const showAdmin = user?.platformRole === 'ADMIN';

  return (
    <aside
      className={
        'flex shrink-0 flex-col border-r border-ink-100 bg-white transition-[width] duration-200 ' +
        (isCollapsed ? 'w-16' : 'w-72')
      }
    >
      <nav className="flex flex-1 flex-col gap-1 p-3">
        {NAV_ITEMS.map((item) => (
          <NavItemLink key={item.label} item={item} isCollapsed={isCollapsed} />
        ))}

        {showAdmin && (
          <>
            <hr className="my-3 border-ink-100" />
            {!isCollapsed && (
              <p className="px-3 pb-1 text-xs font-medium uppercase tracking-wide text-ink-500">
                Administración
              </p>
            )}
            {ADMIN_ITEMS.map((item) => (
              <NavItemLink key={item.label} item={item} isCollapsed={isCollapsed} />
            ))}
          </>
        )}
      </nav>

      <div className="p-3">
        <button
          type="button"
          onClick={() => setIsCollapsed(!isCollapsed)}
          aria-label={isCollapsed ? 'Expandir menú' : 'Colapsar menú'}
          title={isCollapsed ? 'Expandir menú' : 'Colapsar menú'}
          className={
            `${itemBase} ${itemPadding(isCollapsed)} w-full border border-ink-100 font-medium text-ink-500 ` +
            'hover:bg-ink-50 hover:text-ink-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-brand-400'
          }
        >
          <span className="shrink-0">
            {isCollapsed ? <ChevronsRightIcon /> : <ChevronsLeftIcon />}
          </span>
          {!isCollapsed && <span>Colapsar menú</span>}
        </button>
      </div>
    </aside>
  );
}

function NavItemLink({ item, isCollapsed }: { item: NavItem; isCollapsed: boolean }) {
  const label = isCollapsed ? null : <span className="truncate">{item.label}</span>;
  const padding = itemPadding(isCollapsed);

  /* Locked sections keep full-contrast text (ink-500, 5.69:1) and signal their
   * state through the padlock and a lighter weight instead — dimming the label
   * to carry that meaning is what made them unreadable before. */
  if (!item.to) {
    return (
      <span
        aria-disabled="true"
        title={`${item.label} — no disponible`}
        className={`${itemBase} ${padding} cursor-not-allowed font-normal text-ink-500`}
      >
        <span className="relative shrink-0">
          {item.icon}
          {isCollapsed && (
            <LockIcon className="absolute -bottom-1 -right-1 h-3 w-3 rounded-full bg-white text-ink-400" />
          )}
        </span>
        {label}
        {!isCollapsed && <LockIcon className="ml-auto h-3.5 w-3.5 shrink-0 text-ink-400" />}
      </span>
    );
  }

  return (
    <NavLink
      to={item.to}
      title={isCollapsed ? item.label : undefined}
      className={({ isActive }) =>
        `${itemBase} ${padding} ` +
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
