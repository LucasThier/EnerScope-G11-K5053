import { specForType } from '../components/editor/nodeCatalog';
import type { NodeType, Vertical } from '../types/diagram';
import { VERTICAL_LABELS } from '../components/editor/nodeCatalog';
import type { ProjectAccess, VersionAccess } from '../types/versionAccess';

export function findVersionAccess(
  access: ProjectAccess | null | undefined,
  versionId: string | null | undefined,
): VersionAccess | null {
  if (!access || !versionId) {
    return null;
  }
  return access.versions.find((version) => version.versionId === versionId) ?? null;
}

export function canMergeIntoParent(
  access: ProjectAccess | null | undefined,
  parentVersionId: string | null | undefined,
): boolean {
  return findVersionAccess(access, parentVersionId)?.canManage ?? false;
}

export function canBranchFromParent(
  access: ProjectAccess | null | undefined,
  parentVersionId: string | null | undefined,
): boolean {
  return findVersionAccess(access, parentVersionId)?.canEdit ?? false;
}

export const UNKNOWN_VERTICAL = 'UNKNOWN';

export function scenarioVerticals(nodes: { type: string }[]): (Vertical | typeof UNKNOWN_VERTICAL)[] {
  const found = new Set<Vertical | typeof UNKNOWN_VERTICAL>();
  for (const node of nodes) {
    found.add(specForType(node.type as NodeType)?.vertical ?? UNKNOWN_VERTICAL);
  }
  return [...found];
}

export function uneditableVerticals(
  nodes: { type: string }[],
  editable: Vertical[],
): (Vertical | typeof UNKNOWN_VERTICAL)[] {
  return scenarioVerticals(nodes).filter(
    (vertical) => vertical === UNKNOWN_VERTICAL || !editable.includes(vertical),
  );
}

export const ACCESS_FORBIDDEN_MESSAGE = 'No tenés acceso a este proyecto.';

export const ACCESS_UNAVAILABLE_MESSAGE =
  'No pudimos verificar tus permisos. Las acciones de edición quedan deshabilitadas hasta que se puedan cargar.';

export function accessFailureMessage(error: string | null, forbidden: boolean): string | null {
  if (forbidden) {
    return ACCESS_FORBIDDEN_MESSAGE;
  }
  return error ? ACCESS_UNAVAILABLE_MESSAGE : null;
}

export interface VersionEconomics {
  canSimulate: boolean;
  canViewResults: boolean;
  canViewConfiguration: boolean;
  canEditConfiguration: boolean;
  editableVerticals: Vertical[];
}

export function versionEconomics(
  access: ProjectAccess | null | undefined,
  versionId: string | null | undefined,
): VersionEconomics {
  const entry = findVersionAccess(access, versionId);
  return {
    canSimulate: entry?.canSimulate ?? false,
    canViewResults: entry?.canViewEconomicResults ?? true,
    canViewConfiguration: entry?.canViewEconomicConfiguration ?? true,
    canEditConfiguration: entry?.canEditEconomicConfiguration ?? false,
    editableVerticals: entry?.editableVerticals ?? [],
  };
}

export interface ProjectVersioning {
  canManage: boolean;
  canCreateRoot: boolean;
  canEdit: boolean;
  canCreateVersion: boolean;
}

export function projectVersioning(
  access: ProjectAccess | null | undefined,
  versionCount: number,
): ProjectVersioning {
  const canManage = access?.canManageProject ?? false;
  const canCreateRoot = access?.canCreateRootVersions ?? false;
  const canBranchAny = access?.versions.some((version) => version.canEdit) ?? false;
  return {
    canManage,
    canCreateRoot,
    canEdit: canManage || canBranchAny,
    canCreateVersion: versionCount > 0 ? canBranchAny : canCreateRoot,
  };
}

export function scenarioCopyBlockedReason(
  nodes: { type: string }[],
  editable: Vertical[],
  accessFailed: boolean,
): string | null {
  if (accessFailed) {
    return 'No pudimos verificar tus permisos por vertical, por eso no se puede crear la copia.';
  }
  const blocked = uneditableVerticals(nodes, editable);
  if (blocked.length === 0) {
    return null;
  }
  const names = blocked.map((vertical) =>
    vertical === UNKNOWN_VERTICAL ? 'tipo de nodo desconocido' : VERTICAL_LABELS[vertical],
  );
  return (
    'Para crear la copia necesitás poder editar todas las verticales del escenario. Te falta permiso en: ' +
    names.join(', ') +
    '.'
  );
}
