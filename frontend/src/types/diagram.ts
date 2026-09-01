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
  | 'INTERNAL_CONSUMPTION';

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

export interface VersionSummary {
  id: string;
  name: string;
  parentVersionId: string | null;
  createdAt: string;
}

export interface Project {
  id: string;
  name: string;
  description: string;
  organizationId: string;
}
