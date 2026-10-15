/**
 * Types mirroring the backend node/diagram DTOs. Keep in sync with
 * `org.enerscope.node.dto` and `org.enerscope.version.dto`.
 */

export type NodeState = 'RUNNING' | 'PROPOSED' | 'PENDING' | 'REMOVED';

export type Vertical =
  | 'EXTRACTION'
  | 'TRANSPORTATION'
  | 'LIQUEFACTION'
  | 'EXPORT'
  | 'INTERNAL_CONSUMPTION';

export type StructuralRole = 'GENERATOR' | 'INTERMEDIATE' | 'SINK';

/** Mirrors backend NodeTypeEnum (note the pre-existing `LNG_CAMER` spelling). */
export type NodeType =
  | 'WELL'
  | 'GATHERING_NETWORK'
  | 'TREATMENT_PLANT'
  | 'PIPELINE'
  | 'PIPELINE_CONECTION'
  | 'COMPRESSING_PLANT'
  | 'GROUND_LIQUEFACTION_PLANT'
  | 'FLNG_UNIT'
  | 'SEAPORT_TERMINAL'
  | 'LNG_CAMER'
  | 'INTERNAL_CONSUMPTION'
  | 'INDUSTRIAL_CONSUMPTION';

/** Abstract diagram-canvas position (x/y in canvas units). */
export interface GraphPosition {
  x: number | null;
  y: number | null;
}

/** Real-world position, `[longitude, latitude]` (MapLibre order). */
export interface GeographicalPosition {
  longitude: number | null;
  latitude: number | null;
}

export interface NodeGraphData {
  graphPosition: GraphPosition | null;
  geographicalPosition: GeographicalPosition | null;
}

export interface NodeTypeData {
  vertical: Vertical;
  role: StructuralRole;
  nodeType: NodeType;
}

export interface DiagramNode {
  /** Table id — this is what connections reference within a version. */
  id: string;
  /** Cross-version identity. */
  identity: string;
  name: string;
  state: NodeState;
  type: NodeTypeData;
  graphData: NodeGraphData | null;
}

export interface DiagramConnection {
  id: string;
  identity: string;
  fromNodeId: string;
  toNodeId: string;
}

export interface Diagram {
  versionId: string;
  nodes: DiagramNode[];
  connections: DiagramConnection[];
}

export type DistributionType = 'CONSTANT' | 'UNIFORM' | 'NORMAL';

/**
 * A probability distribution, as the backend's ProbabilisticDistributionDTO
 * carries it: CONSTANT (val_a = value), UNIFORM (val_a = min, val_b = max) or
 * NORMAL (val_a = mean, val_b = standard deviation).
 */
export interface Distribution {
  type: DistributionType;
  val_a: number;
  val_b: number | null;
}

/** Mirrors backend CostBasisEnum (note the pre-existing spellings). */
export type CostBasis =
  | 'FLAT'
  | 'PER_M'
  | 'PER_KM'
  | 'PER_KM2'
  | 'PER_CONECTIONS_TOTAL'
  | 'PER_CONECTIONS_INPUT'
  | 'PER_CONECTIONES_OUTPUT';

/** One component of a node's investment cost (its CAPEX). */
export interface InvestmentComponent {
  name: string;
  amount: number;
  costBasis: CostBasis;
}

/** Full detail of a node, for the edit form. */
export interface NodeDetail {
  id: string;
  identity: string;
  name: string;
  state: NodeState;
  type: NodeTypeData;
  graphData: NodeGraphData | null;
  upkeepCosts: number;
  operatingCosts: number;
  lifespanInMonths: number;
  maintenanceIntervalInDays: number;
  /** Hours a maintenance stop lasts. */
  maintenanceDuration: number;
  wastePercentage: number;
  attributes: Record<string, number>;
  /** A well's decline curve, in percent per year; null for other node types. */
  declineCurve: Distribution | null;
  investmentCost: InvestmentComponent[];
}

/** A version (scenario) as lists show it. Mirrors backend VersionSummaryDTO. */
export interface VersionSummary {
  id: string;
  name: string;
  parentVersionId: string | null;
  createdAt: string;
  /** Active nodes; soft-deleted (REMOVED) ones are not counted. */
  nodeCount: number;
  connectionCount: number;
  simulationCount: number;
  lastSimulatedAt: string | null;
}

/** A conflict left by merging a sibling into the shared parent. Mirrors VersionConflictDTO. */
export interface VersionConflict {
  id: string;
  mergedVersionId: string;
  sourceVersionId: string;
  conflictingVersionId: string;
  conflictingVersionName: string;
  entityType: 'NODE' | 'CONNECTION';
  identityId: string;
  mergedDescription: string | null;
  conflictingDescription: string | null;
  sourceChangeType: 'ADD' | 'EDIT' | 'DELETE' | null;
  conflictingChangeType: 'ADD' | 'EDIT' | 'DELETE' | null;
  mergedAt: string;
  resolved: boolean;
  resolvedAt: string | null;
}
