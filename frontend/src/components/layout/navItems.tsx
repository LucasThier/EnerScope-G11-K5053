import type { ReactNode } from 'react';
import {
  BranchIcon,
  BuildingIcon,
  CompareIcon,
  FlaskIcon,
  FolderIcon,
  HomeIcon,
  ReportIcon,
  SettingsIcon,
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
}

/**
 * The sections from the product design. The ones still to be built are listed
 * but locked: showing the full map of the product is intentional, linking to
 * pages that do not exist is not.
 */
export const NAV_ITEMS: NavItem[] = [
  { label: 'Inicio', icon: <HomeIcon />, to: '/app' },
  { label: 'Proyectos', icon: <FolderIcon />, to: '/projects', matchPattern: PROJECTS_PATH_PATTERN },
  { label: 'Mapa de la Cadena de Valor', icon: <ValueChainIcon />, to: '/editor' },
  { label: 'Versionado', icon: <BranchIcon />, to: '/versions', matchPattern: VERSIONS_PATH_PATTERN },
  { label: 'Simulaciones / Escenarios', icon: <FlaskIcon /> },
  { label: 'Comparar Escenarios', icon: <CompareIcon /> },
  { label: 'Reportes', icon: <ReportIcon /> },
  { label: 'Gestión de organización', icon: <TeamIcon />, key: 'team' },
  { label: 'Configuración', icon: <SettingsIcon /> },
];

/** Platform administration, kept separate because it is not part of the project workspace. */
export const ADMIN_ITEMS: NavItem[] = [
  { label: 'Gestión de usuarios', icon: <UsersIcon />, to: '/admin/users' },
  { label: 'Gestión de organizaciones', icon: <BuildingIcon />, to: '/admin/organizations' },
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
