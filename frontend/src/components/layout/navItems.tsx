/* eslint-disable react-refresh/only-export-components -- Navigation data contains icon elements; this module exports no components. */
import { COMPARISON_PATTERN, CONFIGURATION_PATTERN, SIMULATION_PATTERN, VERSION_MANAGEMENT_PATTERN } from '../../utils/workspaceRoutes';
import type { ReactNode } from 'react';
import {
  BranchIcon,
  BuildingIcon,
  CompareIcon,
  FlaskIcon,
  FolderIcon,
  HomeIcon,
  ReportIcon,
  UsersIcon,
  ValueChainIcon,
} from '../ui/icons';

export const VERSIONS_PATH_PATTERN = VERSION_MANAGEMENT_PATTERN;
export const PROJECTS_PATH_PATTERN = /^\/projects\/?$/;
export const PROJECT_TEAM_PATH_PATTERN = /^\/projects\/[^/]+\/team(\/|$)/;
export const VERSIONS_INDEX_PATH_PATTERN = /^\/versions\/?$/;
export const ORGANIZATION_PATH_PATTERN = /^\/organizations\/[^/]+/;
export const MY_ORGANIZATIONS_PATH_PATTERN = /^\/organizations(\/|$)/;

export const MY_ORGANIZATIONS = {
  label: 'Mis organizaciones',
  description: 'Revisá tus organizaciones y gestioná sus integrantes.',
  to: '/organizations',
};

export type NavRequirement =
  | 'platformAdmin'
  | 'organizationMember'
  | 'manageableProject'
  | 'activeProject'
  | 'scenarioConfiguration'
  | 'scenarioSimulation'
  | 'scenarioComparison';

export interface NavItem {
  label: string;
  icon?: ReactNode;
  id?: string;
  /** Absent until the section has a page; the entry renders locked instead of linking nowhere. */
  to?: string;
  matchPattern?: RegExp;
  fullLabel?: string;
  availability?: 'soon';
  requires?: NavRequirement;
  keepsOwnName?: boolean;
  children?: NavItem[];
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
        id: 'projects',
        label: 'Proyectos',
        icon: <FolderIcon />,
        children: [
          {
            label: 'Todos los proyectos',
            to: '/projects',
            matchPattern: PROJECTS_PATH_PATTERN,
          },
          {
            label: 'Equipo del proyecto',
            matchPattern: PROJECT_TEAM_PATH_PATTERN,
            requires: 'manageableProject',
          },
        ],
      },
      {
        label: 'Cadena de valor',
        fullLabel: 'Mapa de la Cadena de Valor',
        icon: <ValueChainIcon />,
        to: '/editor',
      },
      {
        id: 'versions',
        label: 'Versionado',
        icon: <BranchIcon />,
        children: [
          {
            label: 'Todas las versiones',
            to: '/versions',
            matchPattern: VERSIONS_INDEX_PATH_PATTERN,
          },
          {
            label: 'Versiones del proyecto',
            matchPattern: VERSIONS_PATH_PATTERN,
            requires: 'activeProject',
          },
        ],
      },
    ],
  },
  {
    title: 'Análisis',
    items: [
      {
        label: 'Configuración del Escenario',
        icon: <FolderIcon />,
        matchPattern: CONFIGURATION_PATTERN,
        requires: 'scenarioConfiguration',
      },
      {
        label: 'Simulaciones',
        fullLabel: 'Simulación de Escenarios',
        icon: <FlaskIcon />,
        matchPattern: SIMULATION_PATTERN,
        requires: 'scenarioSimulation',
      },
      {
        label: 'Comparaciones',
        fullLabel: 'Comparación de Escenarios',
        icon: <CompareIcon />,
        matchPattern: COMPARISON_PATTERN,
        requires: 'scenarioComparison',
      },
      { label: 'Reportes', icon: <ReportIcon />, availability: 'soon' },
    ],
  },
  /** Platform administration, kept separate because it is not part of the project workspace. */
  {
    title: 'Administración',
    items: [
      {
        id: 'organizations',
        label: 'Organizaciones',
        icon: <BuildingIcon />,
        children: [
          {
            label: MY_ORGANIZATIONS.label,
            to: MY_ORGANIZATIONS.to,
            matchPattern: MY_ORGANIZATIONS_PATH_PATTERN,
            requires: 'organizationMember',
            keepsOwnName: true,
          },
          {
            label: 'Todas',
            fullLabel: 'Todas las organizaciones',
            to: '/admin/organizations',
            requires: 'platformAdmin',
          },
        ],
      },
      {
        label: 'Usuarios',
        fullLabel: 'Gestión de usuarios',
        icon: <UsersIcon />,
        to: '/admin/users',
        requires: 'platformAdmin',
      },
    ],
  },
];

const MODULE_TITLES: { pattern: RegExp; title: string }[] = [
  { pattern: /^\/app\/?$/, title: 'Inicio' },
  { pattern: CONFIGURATION_PATTERN, title: 'Configuración del Escenario' },
  { pattern: SIMULATION_PATTERN, title: 'Simulación de Escenarios' },
  { pattern: COMPARISON_PATTERN, title: 'Comparación de Escenarios' },
  { pattern: VERSIONS_PATH_PATTERN, title: 'Versionado' },
  { pattern: /^\/projects\/[^/]+\/team(\/|$)/, title: 'Equipo del proyecto' },
  { pattern: /^\/projects\/?$/, title: 'Proyectos' },
  { pattern: /^\/versions\/?$/, title: 'Versionado' },
  { pattern: /^\/editor(\/|$)/, title: 'Mapa de la Cadena de Valor' },
  { pattern: /^\/organizations\/?$/, title: 'Mis organizaciones' },
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

const PROJECT_SCOPED = /^\/projects\/[^/]+\/(team|versions|scenario-configurations|simulations|comparisons)(\/|$)/;

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
  if (ORGANIZATION_PATH_PATTERN.test(pathname)) {
    return [{ label: MY_ORGANIZATIONS.label, to: MY_ORGANIZATIONS.to }, { label: title }];
  }
  if (pathname.startsWith('/admin/')) {
    return [{ label: 'Administración' }, { label: title }];
  }
  return [{ label: title }];
}

export function documentSectionFor(
  pathname: string,
  projectName: string | null,
  organizationName: string | null,
): string | null {
  const crumbs = breadcrumbsFor(pathname, projectName);
  if (crumbs.length === 0) {
    return null;
  }
  const section = crumbs[crumbs.length - 1].label;
  const entity = ORGANIZATION_PATH_PATTERN.test(pathname)
    ? organizationName
    : PROJECT_SCOPED.test(pathname)
      ? projectName
      : null;
  return entity ? `${section} · ${entity}` : section;
}
