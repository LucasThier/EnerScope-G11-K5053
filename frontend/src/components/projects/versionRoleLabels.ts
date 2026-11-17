import { VERTICAL_LABELS } from '../editor/nodeCatalog';
import type { Vertical } from '../../types/diagram';
import type { ProjectMemberType } from '../../types/project';
import type { VersionRoleType } from '../../types/versionAccess';

export const VERSION_ROLE_LABELS: Record<VersionRoleType, string> = {
  ADMIN: 'Administrador de versión',
  EDITOR: 'Editor',
  SIMULATOR: 'Simulador',
};

export const PROJECT_ROLE_ACCESS_LABELS: Record<ProjectMemberType, string> = {
  ADMIN: 'Administrador del proyecto',
  EDITOR: 'Editor del proyecto',
  EXECUTIVE: 'Ejecutivo',
};

export const VERSION_ROLE_SUMMARIES: Record<VersionRoleType, string> = {
  ADMIN: 'Ve, edita todo, simula y administra la versión: borrar, fusionar, resolver conflictos.',
  EDITOR: 'Ve y edita solo las verticales que elijas. No simula ni administra.',
  SIMULATOR: 'Ve y ejecuta simulaciones. No edita ni administra.',
};

export const PLATFORM_ADMIN_LABEL = 'Administrador de plataforma';

export const ALL_VERSIONS_ADMIN_LABEL = 'Administra todas las versiones';

export function verticalLabel(vertical: Vertical): string {
  return VERTICAL_LABELS[vertical];
}

export function versionRoleLabel(type: VersionRoleType, vertical: Vertical | null): string {
  if (type === 'EDITOR' && vertical) {
    return `Editor de ${VERTICAL_LABELS[vertical]}`;
  }
  return VERSION_ROLE_LABELS[type];
}
