import type { ChangeType, ConflictEntityType, VersionConflict } from '../../types/diagram';

const ENTITY_LABELS: Record<ConflictEntityType, string> = {
  NODE: 'Nodo',
  CONNECTION: 'Conexión',
};

const CHANGE_LABELS: Record<ChangeType, string> = {
  ADD: 'Agregado',
  EDIT: 'Editado',
  DELETE: 'Eliminado',
};

export function ownChangeLabel(changeType: ChangeType | null): string {
  return changeType === null ? 'Sin cambios propios' : CHANGE_LABELS[changeType];
}

export function mergedChangeLabel(changeType: ChangeType | null): string {
  return changeType === null ? 'Sin cambios registrados' : CHANGE_LABELS[changeType];
}

export function conflictElementLabel(conflict: VersionConflict): string {
  const description = conflict.mergedDescription ?? conflict.conflictingDescription;
  return ENTITY_LABELS[conflict.entityType] + (description ? ` · ${description}` : '');
}

export function withDescription(label: string, description: string | null): string {
  return description ? `${label} · ${description}` : label;
}
