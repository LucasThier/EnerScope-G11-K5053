/**
 * Types mirroring the backend simulation DTOs (`org.enerscope.simulation.dto`).
 * Volumes are totals over the run's horizon in the model's own units (the
 * capacities are entered per hour). Percentiles are statistical: P10 is the low
 * case and P90 the high one.
 */

export interface SimulationKpis {
  deliveredMean: number;
  deliveredP10: number;
  deliveredP50: number;
  deliveredP90: number;
  exportedP50: number;
  domesticP50: number;
  lossesP50: number;
  capex: number;
  opex: number;
  upkeep: number;
  totalCost: number;
  /** Total cost over the P50 delivered volume; null when nothing was delivered. */
  unitCost: number | null;
}

/** Codes the backend attaches to a run or a comparison; the UI turns them into copy. */
export type SimulationWarning =
  | 'UNSIMULATED_NODES'
  | 'NO_SINKS'
  | 'MISSING_SIMULATIONS'
  | 'DIFFERENT_HORIZONS';

export interface SimulationSummary {
  id: string;
  versionId: string;
  createdAt: string;
  years: number;
  rounds: number;
  nodeCount: number;
  kpis: SimulationKpis;
  warnings: SimulationWarning[];
}

export interface NodeResult {
  nodeId: string;
  identityId: string | null;
  name: string;
  nodeType: string | null;
  vertical: string | null;
  state: string | null;
  producedP10: number | null;
  producedP50: number | null;
  producedP90: number | null;
  capacityP50: number | null;
  utilizationP50: number | null;
  lossesP50: number | null;
  capex: number;
  opex: number;
  upkeep: number;
  totalCost: number;
}

export interface VerticalResult {
  vertical: string;
  nodeCount: number;
  /** Null in a cost estimate, where nothing was simulated. */
  lossesP50: number | null;
  capex: number;
  opex: number;
  upkeep: number;
  totalCost: number;
}

export interface SimulationDetail {
  summary: SimulationSummary;
  verticals: VerticalResult[];
  nodes: NodeResult[];
}

export interface CostEstimate {
  years: number;
  capex: number;
  opex: number;
  upkeep: number;
  totalCost: number;
  verticals: VerticalResult[];
}

export interface RunSimulationRequest {
  years: number;
  rounds?: number;
}

export type NodeChangeKind = 'ADDED' | 'REMOVED' | 'MODIFIED';

export interface NodeDiff {
  identityId: string;
  name: string;
  nodeType: string | null;
  change: NodeChangeKind;
  changedFields: string[];
}

export interface StructuralDiff {
  added: number;
  removed: number;
  modified: number;
  unchanged: number;
  connectionsAdded: number;
  connectionsRemoved: number;
  nodes: NodeDiff[];
}

export interface ComparedVersion {
  versionId: string;
  name: string;
  parentVersionId: string | null;
  createdAt: string;
  nodeCount: number;
  connectionCount: number;
  nodesByVertical: Record<string, number>;
  /** Null for the baseline. */
  diff: StructuralDiff | null;
  /** Null when the version was never simulated. */
  latestSimulation: SimulationSummary | null;
  verticals: VerticalResult[] | null;
}

export interface VersionComparison {
  projectId: string;
  baselineVersionId: string;
  versions: ComparedVersion[];
  warnings: SimulationWarning[];
}
