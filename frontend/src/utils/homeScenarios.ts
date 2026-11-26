import type { VersionSummary } from '../types/diagram';
import type { ScenarioSummary } from '../types/scenario';
import type { ProjectAccess } from '../types/versionAccess';
import { MY_ORGANIZATIONS, leavesOf, type NavGroup } from '../components/layout/navItems';
import {
  DRAFT_HINT, SCENARIO_STATUS_HINTS, scenarioPermissions, scenarioStatusView, simulateBlockedReason,
  type ScenarioPermissions, type StatusTone,
} from './scenarioRules';
import { scenarioConfigurationPath, scenarioHistoryPath, scenarioResultsPath } from './scenarioRoutes';

/* ---- chips: the status, plus "Borrador" when the flag is set ---------------------------------------------------- */

export interface HomeChip {
  id: 'status' | 'draft';
  label: string;
  tone: StatusTone;
  /** One-line meaning, for the tooltip and the accessible name. */
  hint: string;
  /** The chip that stays when the row is too narrow for both (the other one collapses). */
  essential: boolean;
}

/** Lower wins: Simulando > Borrador > Desactualizado / Sin simular > Al día. */
function rank(chip: Pick<HomeChip, 'id' | 'label'>, scenario: ScenarioSummary): number {
  if (chip.id === 'draft') return 1;
  if (scenario.status === 'SIMULATING') return 0;
  return scenario.status === 'UP_TO_DATE' ? 3 : 2;
}

/**
 * The chips of one scenario row, in display order (status first, then "Borrador"). When both exist, the one that
 * wins by priority is `essential`; the other is the one the row drops when it lacks the room.
 */
export function scenarioChips(scenario: ScenarioSummary): HomeChip[] {
  const view = scenarioStatusView(scenario.status);
  const status: HomeChip = { id: 'status', label: view.label, tone: view.tone, hint: SCENARIO_STATUS_HINTS[scenario.status], essential: true };
  if (!scenario.draftPending) return [status];
  const draft: HomeChip = { id: 'draft', label: 'Borrador', tone: 'warn', hint: DRAFT_HINT, essential: true };
  const draftWins = rank(draft, scenario) < rank(status, scenario);
  return [{ ...status, essential: !draftWins }, { ...draft, essential: draftWins }];
}

/* ---- the action of a row -------------------------------------------------------------------------------------- */

export type RowAction = { kind: 'simulate' } | { kind: 'view'; to: string } | { kind: 'none' };

/**
 * Simular when the scenario needs a run (never simulated or outdated) and the person may start it; Ver otherwise,
 * where the draft or an unconfigured scenario opens its configuration, one with results its results, and the rest its
 * history. Nothing while it is simulating, or when the person cannot even view the version.
 */
export function scenarioRowAction(
  scenario: ScenarioSummary,
  permissions: ScenarioPermissions,
  ids: { projectId: string; versionId: string },
): RowAction {
  if (!permissions.canView || scenario.status === 'SIMULATING') return { kind: 'none' };
  const needsRun = scenario.status === 'OUTDATED' || scenario.status === 'NEVER_SIMULATED';
  if (needsRun && simulateBlockedReason(permissions, scenario) === null) return { kind: 'simulate' };
  const { projectId, versionId } = ids;
  if (scenario.draftPending || !scenario.configured) return { kind: 'view', to: scenarioConfigurationPath(projectId, versionId, scenario.id) };
  if (scenario.latestEvaluatedAt) return { kind: 'view', to: scenarioResultsPath(projectId, versionId, scenario.id) };
  return { kind: 'view', to: scenarioHistoryPath(projectId, versionId, scenario.id) };
}

/* ---- which version, which rows --------------------------------------------------------------------------------- */

function timeOf(iso: string): number {
  const time = new Date(iso).getTime();
  return Number.isNaN(time) ? Number.POSITIVE_INFINITY : time;
}

/**
 * The version whose scenarios Inicio shows: the project's root (no parent), the oldest one if there are several, among
 * the versions the person can view; with no visible root, the oldest visible version. null when there is none.
 */
export function pickHomeVersion(versions: VersionSummary[], access: ProjectAccess | null | undefined): VersionSummary | null {
  const visible = versions.filter((version) => scenarioPermissions(access, version.id).canView);
  const roots = visible.filter((version) => version.parentVersionId === null);
  const pool = roots.length > 0 ? roots : visible;
  return [...pool].sort((a, b) => timeOf(a.createdAt) - timeOf(b.createdAt))[0] ?? null;
}

export const SCENARIO_ROWS = 5;
/** On a short screen (1280×720) one row less, so the page keeps fitting without a scroll. */
export const SCENARIO_ROWS_SHORT = 4;

export function scenarioRowLimit(shortScreen: boolean): number {
  return shortScreen ? SCENARIO_ROWS_SHORT : SCENARIO_ROWS;
}

/** The version's default scenario first, then the most recently modified; cut to `limit`. */
export function homeScenarioRows(scenarios: ScenarioSummary[], limit: number): { rows: ScenarioSummary[]; hidden: number } {
  const ordered = [...scenarios].sort((a, b) => {
    if (a.defaultScenario !== b.defaultScenario) return a.defaultScenario ? -1 : 1;
    const byDate = timeOf(b.lastModified) - timeOf(a.lastModified);
    return byDate !== 0 && !Number.isNaN(byDate) ? byDate : a.name.localeCompare(b.name, 'es');
  });
  return { rows: ordered.slice(0, limit), hidden: Math.max(0, ordered.length - limit) };
}

/* ---- the footer of the Escenarios block: the next step --------------------------------------------------------------- */

/** From this many rows on, the block is full and carries no footer. */
export const FILLED_ROWS = 4;

/** Scenarios that need a run: never simulated or outdated (a running one is not pending). */
export function pendingScenarios(scenarios: Pick<ScenarioSummary, 'status'>[]): number {
  return scenarios.filter((scenario) => scenario.status === 'OUTDATED' || scenario.status === 'NEVER_SIMULATED').length;
}

export type ScenarioFooter =
  | { kind: 'create'; canCreate: boolean }
  | { kind: 'add'; canCreate: boolean }
  | { kind: 'pending'; count: number }
  | { kind: 'comparison' }
  | null;

/**
 * What sits under the rows, never repeating them: no scenario → say so and offer to create one; one → suggest a second
 * (a comparison needs two); two or three with something to simulate → how many; all up to date → the last saved
 * comparison, if there is one. Four or more rows fill the block and take no footer. `canCreate` is the rule of the
 * scenario list (`createBlockedReason`): the button is offered only to who may create.
 */
export function scenarioFooter(input: {
  total: number;
  rows: number;
  pending: number;
  canCreate: boolean;
  hasLastComparison: boolean;
}): ScenarioFooter {
  if (input.total === 0) return { kind: 'create', canCreate: input.canCreate };
  if (input.total === 1) return { kind: 'add', canCreate: input.canCreate };
  if (input.rows >= FILLED_ROWS) return null;
  if (input.pending > 0) return { kind: 'pending', count: input.pending };
  return input.hasLastComparison ? { kind: 'comparison' } : null;
}

/** "1 escenario por simular" / "3 escenarios por simular". */
export function pendingLabel(count: number): string {
  return `${count} ${count === 1 ? 'escenario' : 'escenarios'} por simular`;
}

/** Why Comparar may not lead anywhere yet: the screen needs two scenarios (`MIN_SCENARIOS`); null when it will. */
export const COMPARE_MIN_SCENARIOS = 2;
export const COMPARE_NEEDS_TWO = 'Se necesitan al menos 2 escenarios';
export function compareHint(scenarioCount: number): string | undefined {
  return scenarioCount < COMPARE_MIN_SCENARIOS ? COMPARE_NEEDS_TWO : undefined;
}

/* ---- quick access chips --------------------------------------------------------------------------------------- */

export type QuickActionId = 'new' | 'compare' | 'users' | 'organizations' | 'myOrganizations' | 'profile';

export interface QuickAction {
  id: QuickActionId;
  label: string;
  /** The description that used to sit under the title; now the tooltip and accessible name. */
  hint: string;
  to?: string;
}

export const MAX_QUICK_ACTIONS = 4;

/**
 * The chips of "Accesos rápidos". Permissions are the sidebar's: a section is offered only when the resolved
 * navigation (`useNavigationTree`) reaches it. Administrators get Nuevo proyecto, Comparar escenarios, Usuarios and
 * Organizaciones; everyone else Nuevo proyecto, Comparar escenarios, Mis organizaciones and Mi perfil. Whatever the
 * person cannot reach is left out, never greyed.
 */
export function quickActionsFor(
  groups: NavGroup[],
  options: { isAdmin: boolean; canCreateProject: boolean; compareProjectId?: string | null },
): QuickAction[] {
  const leaves = leavesOf(groups);
  const reach = (to: string) => leaves.some((leaf) => leaf.to === to);
  const compareLeaf = leaves.find((leaf) => leaf.requires === 'scenarioComparison' && leaf.to !== undefined);

  const newProject: QuickAction | null = options.canCreateProject
    ? { id: 'new', label: 'Nuevo proyecto', hint: 'Creá un proyecto en una de tus organizaciones.' }
    : null;
  const compare: QuickAction | null = compareLeaf
    ? {
        id: 'compare',
        label: 'Comparar escenarios',
        hint: compareLeaf.fullLabel ?? 'Comparación de Escenarios',
        to: options.compareProjectId ? `/projects/${encodeURIComponent(options.compareProjectId)}/comparisons` : compareLeaf.to,
      }
    : null;
  const chips: (QuickAction | null)[] = options.isAdmin
    ? [
        newProject,
        compare,
        reach('/admin/users') ? { id: 'users', label: 'Usuarios', hint: 'Administrá las cuentas de la plataforma.', to: '/admin/users' } : null,
        reach('/admin/organizations') ? { id: 'organizations', label: 'Organizaciones', hint: 'Gestioná las organizaciones.', to: '/admin/organizations' } : null,
      ]
    : [
        newProject,
        compare,
        reach(MY_ORGANIZATIONS.to) ? { id: 'myOrganizations', label: MY_ORGANIZATIONS.label, hint: MY_ORGANIZATIONS.description, to: MY_ORGANIZATIONS.to } : null,
        { id: 'profile', label: 'Mi perfil', hint: 'Actualizá tus datos y tu contraseña.', to: '/profile' },
      ];
  return chips.filter((chip): chip is QuickAction => chip !== null).slice(0, MAX_QUICK_ACTIONS);
}
