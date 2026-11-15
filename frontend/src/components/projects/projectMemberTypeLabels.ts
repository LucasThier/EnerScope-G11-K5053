import type { ProjectMemberType } from '../../types/project';

export const PROJECT_MEMBER_TYPE_LABELS: Record<ProjectMemberType, string> = {
  ADMIN: 'Administrador del proyecto',
  EDITOR: 'Editor del proyecto',
};

export const EDITOR_LIMITS = 'Un Editor no administra integrantes ni edita el proyecto.';

export const INACTIVE_ACCOUNT_LABEL = 'Cuenta inactiva';

export const INACTIVE_ACCOUNT_TITLE =
  'La cuenta de esta persona está desactivada en la plataforma. No describe su membresía en el proyecto.';
