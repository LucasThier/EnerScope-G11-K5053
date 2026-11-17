export const NO_EDITABLE_PARENT_MESSAGE =
  'No tenés permiso para editar ninguna versión de este proyecto, así que no podés crear una versión derivada.';

export const FIRST_VERSION_ADMIN_MESSAGE =
  'Pedile al administrador del proyecto que cree la primera versión.';

export const NAME_REQUIRED_MESSAGE = 'El nombre es obligatorio.';

export type NewVersionMode = 'loading' | 'blocked-first' | 'blocked-no-parent' | 'form';

interface NewVersionModeInput {
  loading: boolean;
  versionCount: number;
  parentCount: number;
  canCreateRoot: boolean;
}

export function newVersionMode({
  loading,
  versionCount,
  parentCount,
  canCreateRoot,
}: NewVersionModeInput): NewVersionMode {
  if (loading) {
    return 'loading';
  }
  if (versionCount === 0 && !canCreateRoot) {
    return 'blocked-first';
  }
  if (versionCount > 0 && parentCount === 0) {
    return 'blocked-no-parent';
  }
  return 'form';
}

export function newVersionBlockMessage(mode: NewVersionMode): string | null {
  if (mode === 'blocked-first') {
    return FIRST_VERSION_ADMIN_MESSAGE;
  }
  return mode === 'blocked-no-parent' ? NO_EDITABLE_PARENT_MESSAGE : null;
}

export function pickDefaultParent(
  parents: { id: string; parentVersionId: string | null }[],
  defaultParentId?: string,
): string | null {
  if (parents.length === 0) {
    return null;
  }
  if (defaultParentId && parents.some((version) => version.id === defaultParentId)) {
    return defaultParentId;
  }
  return (parents.find((version) => version.parentVersionId === null) ?? parents[0]).id;
}

export function validateVersionName(
  raw: string,
): { name: string; error: null } | { name: null; error: string } {
  const name = raw.trim();
  return name ? { name, error: null } : { name: null, error: NAME_REQUIRED_MESSAGE };
}

export function parentForNewVersion(versionCount: number, parentVersionId: string): string | null {
  return versionCount > 0 ? parentVersionId : null;
}
