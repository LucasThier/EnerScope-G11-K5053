import type { ProjectAccess } from '../types/versionAccess';
import type { ScenarioStatus, ScenarioSummary } from '../types/scenario';
import { findVersionAccess } from './versionAccessRules';

/** A version holds at most this many scenarios (the backend enforces it too). */
export const MAX_SCENARIOS_PER_VERSION = 20;
export const SCENARIO_NAME_MAX = 120;

export const OUTDATED_NOTICE = 'La base cambió: revisá este escenario.';
export const OUTDATED_DETAIL = 'Desde la última simulación cambió la configuración de este escenario o los nodos de su versión. Volvé a simular para actualizar los resultados.';
export const IGNORED_OPERATIONS_NOTICE = 'Este borrador traía cambios de operación; editalos en el nodo.';
export const DELETE_SCENARIO_NOTE = 'Sus resultados y las comparaciones guardadas que lo usaron se conservan.';

export interface ScenarioPermissions {
  canView: boolean;
  /** Create, duplicate, rename and edit the configuration. */
  canEdit: boolean;
  canDelete: boolean;
  canSimulate: boolean;
  /** Why an action is unavailable, for a disabled button's tooltip; null when it is allowed. */
  reasons: { edit: string | null; delete: string | null; simulate: string | null };
}

const NO_ACCESS: ScenarioPermissions = {
  canView: false, canEdit: false, canDelete: false, canSimulate: false,
  reasons: {
    edit: 'Todavía no se verificaron tus permisos.',
    delete: 'Todavía no se verificaron tus permisos.',
    simulate: 'Todavía no se verificaron tus permisos.',
  },
};

/**
 * What the person may do with the scenarios of a version, from `/projects/{id}/access`. Deny by default:
 * no access data (loading or failed) allows nothing. The backend repeats every rule: creating, duplicating, renaming
 * and editing need the right to edit the version; deleting needs to administer it; simulating needs the right to
 * simulate it; the read-only Ejecutivo only reads.
 */
export function scenarioPermissions(access: ProjectAccess | null | undefined, versionId: string | null | undefined): ScenarioPermissions {
  if (!access) return NO_ACCESS;
  const entry = findVersionAccess(access, versionId);
  const editsProject = access.platformAdmin || access.projectPermissions.includes('EDIT_PROJECT');
  const canView = entry ? entry.canView : access.platformAdmin || access.projectPermissions.includes('VIEW_PROJECT');
  const readOnly = !editsProject && access.projectRole === 'EXECUTIVE';
  const readOnlyReason = 'Tu rol es de solo lectura: podés ver y comparar escenarios, pero no modificarlos.';
  const canEdit = editsProject && entry?.canEdit === true;
  const canDelete = editsProject && entry?.canManage === true;
  const canSimulate = editsProject && entry?.canSimulate === true;
  return {
    canView,
    canEdit,
    canDelete,
    canSimulate,
    reasons: {
      edit: canEdit ? null : readOnly ? readOnlyReason : 'Necesitás permiso para editar esta versión.',
      delete: canDelete ? null : readOnly ? readOnlyReason : 'Solo quien administra la versión puede eliminar escenarios.',
      simulate: canSimulate ? null : readOnly ? readOnlyReason : 'Necesitás permiso para simular esta versión.',
    },
  };
}

export type NameCheck = { ok: true; name: string } | { ok: false; error: string };

/**
 * The name the person typed for a new, duplicated or renamed scenario. It is required (never generated), at most
 * 120 characters and unique in the version ignoring case; `selfId` lets a scenario keep (or re-case) its own name.
 * This only saves a round trip: the backend decides.
 */
export function validateScenarioName(raw: string, others: Pick<ScenarioSummary, 'id' | 'name'>[], selfId?: string): NameCheck {
  const name = raw.trim();
  if (!name) return { ok: false, error: 'Escribí un nombre para el escenario.' };
  if (name.length > SCENARIO_NAME_MAX) return { ok: false, error: `El nombre admite hasta ${SCENARIO_NAME_MAX} caracteres.` };
  const key = name.toLocaleLowerCase('es');
  if (others.some(other => other.id !== selfId && other.name.trim().toLocaleLowerCase('es') === key)) {
    return { ok: false, error: 'Ya hay un escenario con ese nombre en esta versión (no se distinguen mayúsculas).' };
  }
  return { ok: true, name };
}

export function limitReached(count: number): boolean {
  return count >= MAX_SCENARIOS_PER_VERSION;
}

export type StatusTone = 'good' | 'warn' | 'neutral' | 'info';
export interface ScenarioStatusView { label: string; tone: StatusTone; detail: string }

/** The status the API returns, in plain words. */
export function scenarioStatusView(status: ScenarioStatus): ScenarioStatusView {
  switch (status) {
    case 'UP_TO_DATE':
      return { label: 'Al día', tone: 'good', detail: 'Su última simulación corresponde a la configuración y a la base actuales.' };
    case 'OUTDATED':
      return { label: 'Desactualizado', tone: 'warn', detail: OUTDATED_DETAIL };
    case 'SIMULATING':
      return { label: 'Simulando…', tone: 'info', detail: 'Hay una simulación en cola o en curso.' };
    default:
      return { label: 'Sin simular', tone: 'neutral', detail: 'Todavía no tiene resultados.' };
  }
}

/** Which scenario the old version-level routes act on. */
export function defaultScenarioOf(scenarios: ScenarioSummary[]): ScenarioSummary | null {
  return scenarios.find(scenario => scenario.defaultScenario) ?? scenarios[0] ?? null;
}

/** Why a scenario cannot be simulated right now (permission first, then its own state); null when it can. */
export function simulateBlockedReason(permissions: ScenarioPermissions, scenario: ScenarioSummary): string | null {
  if (!permissions.canSimulate) return permissions.reasons.simulate;
  if (!scenario.configured) return 'Configurá el escenario antes de simularlo.';
  if (scenario.draftPending) return 'Tiene un borrador pendiente: completalo o descartalo antes de simular.';
  if (scenario.status === 'SIMULATING') return 'Ya hay una simulación en curso.';
  return null;
}

/** Why a scenario cannot be created or duplicated now; null when it can. */
export function createBlockedReason(permissions: ScenarioPermissions, count: number): string | null {
  if (!permissions.canEdit) return permissions.reasons.edit;
  if (limitReached(count)) return `Esta versión ya tiene el máximo de ${MAX_SCENARIOS_PER_VERSION} escenarios. Eliminá alguno para crear otro.`;
  return null;
}
