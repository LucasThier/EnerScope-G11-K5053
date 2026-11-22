/**
 * Paths of the scenario flow: choose a version (the physical base), then one of its scenarios (the economic and
 * simulation configuration), then edit or simulate it. The version-level economics routes of earlier releases
 * (`/versions/:v/economics/...`) redirect to the version's default scenario.
 */
const enc = encodeURIComponent;

export const projectVersionsPath = (projectId: string) => `/projects/${enc(projectId)}/scenario-configurations`;
export const projectSimulationsPath = (projectId: string) => `/projects/${enc(projectId)}/simulations`;

/** The scenarios of one version (configuration module). */
export const versionScenariosPath = (projectId: string, versionId: string) =>
  `/projects/${enc(projectId)}/versions/${enc(versionId)}/scenarios`;
/** The scenarios of one version (simulation module). */
export const versionSimulationsPath = (projectId: string, versionId: string) =>
  `/projects/${enc(projectId)}/versions/${enc(versionId)}/simulations`;

export const scenarioConfigurationPath = (projectId: string, versionId: string, scenarioId: string) =>
  `${versionScenariosPath(projectId, versionId)}/${enc(scenarioId)}/configuration`;
export const scenarioHistoryPath = (projectId: string, versionId: string, scenarioId: string) =>
  `${versionScenariosPath(projectId, versionId)}/${enc(scenarioId)}/history`;
export const scenarioResultsPath = (projectId: string, versionId: string, scenarioId: string, evaluationId?: string) =>
  `${versionScenariosPath(projectId, versionId)}/${enc(scenarioId)}/results` + (evaluationId ? `/${enc(evaluationId)}` : '');

/** Node values ("Operación") belong to the version: they are edited on the node, in the version's editor. */
export const versionOperationPath = (versionId: string) => `/editor?versionId=${enc(versionId)}`;

export type LegacyEconomicsTab = 'configuration' | 'history' | 'results';
export interface LegacyEconomicsRoute {
  projectId: string;
  versionId: string;
  tab: LegacyEconomicsTab;
  evaluationId?: string;
}

const LEGACY = /^\/projects\/([^/]+)\/versions\/([^/]+)\/economics(?:\/(configuration|history|results)(?:\/([^/]+))?)?\/?$/;

/** Reads an old `/projects/:p/versions/:v/economics[/configuration|history|results[/:evaluationId]]` path. */
export function parseLegacyEconomicsPath(pathname: string): LegacyEconomicsRoute | null {
  const match = LEGACY.exec(pathname);
  if (!match) return null;
  const tab = (match[3] as LegacyEconomicsTab | undefined) ?? 'configuration';
  if (tab === 'configuration' && match[4]) return null;
  return { projectId: decodeURIComponent(match[1]), versionId: decodeURIComponent(match[2]), tab, evaluationId: match[4] ? decodeURIComponent(match[4]) : undefined };
}

/** Where an old economics route goes once the version's default scenario is known. */
export function legacyEconomicsTarget(route: LegacyEconomicsRoute, defaultScenarioId: string): string {
  switch (route.tab) {
    case 'history':
      return scenarioHistoryPath(route.projectId, route.versionId, defaultScenarioId);
    case 'results':
      return scenarioResultsPath(route.projectId, route.versionId, defaultScenarioId, route.evaluationId);
    default:
      return scenarioConfigurationPath(route.projectId, route.versionId, defaultScenarioId);
  }
}
