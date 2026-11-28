/** Where a scenario's latest simulation stands (the same values the comparison API uses). */
export type ScenarioStatus = 'UP_TO_DATE' | 'OUTDATED' | 'NEVER_SIMULATED' | 'SIMULATING';

/**
 * A scenario of a version: its economic and simulation configuration (the version is the physical base).
 * `/projects/{p}/versions/{v}/scenarios` and `/projects/{p}/scenarios/{s}`.
 */
export interface ScenarioSummary {
  id: string;
  versionId: string;
  name: string;
  /** The scenario the old version-level routes act on. */
  defaultScenario: boolean;
  configured: boolean;
  draftPending: boolean;
  status: ScenarioStatus;
  latestEvaluatedAt: string | null;
  createdAt: string;
  lastModified: string;
}
