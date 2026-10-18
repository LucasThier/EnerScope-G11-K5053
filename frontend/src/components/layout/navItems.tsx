import type { ReactNode } from 'react';
import {
  BranchIcon,
  BuildingIcon,
  CompareIcon,
  FlaskIcon,
  FolderIcon,
  HomeIcon,
  ReportIcon,
  TeamIcon,
  UsersIcon,
  ValueChainIcon,
} from '../ui/icons';

export const VERSIONS_PATH_PATTERN = /^\/projects\/[^/]+\/versions(\/|$)/;
export const PROJECTS_PATH_PATTERN = /^\/projects(\/[^/]+\/team)?\/?$/;

export interface NavItem {
  label: string;
  icon: ReactNode;
  key?: string;
  /** Absent until the section has a page; the entry renders locked instead of linking nowhere. */
  to?: string;
  matchPattern?: RegExp;
  fullLabel?: string;
  availability?: 'soon';
  adminOnly?: boolean;
}

export interface NavGroup {
  title: string;
  items: NavItem[];
}

/**
 * The sections from the product design. The ones still to be built are listed
 * but locked: showing the full map of the product is intentional, linking to
 * pages that do not exist is not.
 */
export const NAV_GROUPS: NavGroup[] = [
  {
    title: 'Trabajo',
    items: [
      { label: 'Inicio', icon: <HomeIcon />, to: '/app' },
      {
        label: 'Proyectos',
        icon: <FolderIcon />,
        to: '/projects',
        matchPattern: PROJECTS_PATH_PATTERN,
      },
      {
        label: 'Cadena de valor',
        fullLabel: 'Mapa de la Cadena de Valor',
        icon: <ValueChainIcon />,
        to: '/editor',
      },
      {
        label: 'Versionado',
        icon: <BranchIcon />,
        to: '/versions',
        matchPattern: VERSIONS_PATH_PATTERN,
      },
    ],
  },
  {
    title: 'Análisis',
    items: [
      {
        label: 'Simulaciones',
        fullLabel: 'Simulaciones / Escenarios',
        icon: <FlaskIcon />,
        availability: 'soon',
      },
      { label: 'Comparaciones', icon: <CompareIcon />, availability: 'soon' },
      { label: 'Reportes', icon: <ReportIcon />, availability: 'soon' },
    ],
  },
  /** Platform administration, kept separate because it is not part of the project workspace. */
  {
    title: 'Administración',
    items: [
      {
        label: 'Organización',
        fullLabel: 'Gestión de organización',
        icon: <TeamIcon />,
        key: 'team',
      },
      {
        label: 'Usuarios',
        fullLabel: 'Gestión de usuarios',
        icon: <UsersIcon />,
        to: '/admin/users',
        adminOnly: true,
      },
      {
        label: 'Organizaciones',
        fullLabel: 'Gestión de organizaciones',
        icon: <BuildingIcon />,
        to: '/admin/organizations',
        adminOnly: true,
      },
    ],
  },
];

const MODULE_TITLES: { pattern: RegExp; title: string }[] = [
  { pattern: /^\/app\/?$/, title: 'Inicio' },
  { pattern: VERSIONS_PATH_PATTERN, title: 'Versionado' },
  { pattern: /^\/projects\/[^/]+\/team(\/|$)/, title: 'Equipo del proyecto' },
  { pattern: /^\/projects\/?$/, title: 'Proyectos' },
  { pattern: /^\/versions\/?$/, title: 'Versionado' },
  { pattern: /^\/editor(\/|$)/, title: 'Mapa de la Cadena de Valor' },
  { pattern: /^\/organizations\/[^/]+/, title: 'Gestión de organización' },
  { pattern: /^\/admin\/users(\/|$)/, title: 'Gestión de usuarios' },
  { pattern: /^\/admin\/organizations(\/|$)/, title: 'Gestión de organizaciones' },
  { pattern: /^\/profile\/?$/, title: 'Mi perfil' },
];

export function moduleTitleFor(pathname: string): string | null {
  return MODULE_TITLES.find((entry) => entry.pattern.test(pathname))?.title ?? null;
}

export interface Crumb {
  label: string;
  to?: string;
}

const PROJECT_SCOPED = /^\/projects\/[^/]+\/(team|versions)(\/|$)/;

export function breadcrumbsFor(pathname: string, projectName: string | null): Crumb[] {
  const title = moduleTitleFor(pathname);
  if (!title) {
    return [];
  }
  if (PROJECT_SCOPED.test(pathname)) {
    const trail: Crumb[] = [{ label: 'Proyectos', to: '/projects' }];
    if (projectName) {
      trail.push({ label: projectName });
    }
    return [...trail, { label: title }];
  }
  if (pathname.startsWith('/admin/')) {
    return [{ label: 'Administración' }, { label: title }];
  }
  return [{ label: title }];
}
