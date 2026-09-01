import type { NodeState, NodeType, StructuralRole, Vertical } from '../../types/diagram';

/** A numeric, type-specific field of a node subtype. */
export interface NodeFieldSpec {
  key: string;
  label: string;
  /** Sent as an integer (backend `int`) rather than a float. */
  integer?: boolean;
}

export interface NodeTypeSpec {
  type: NodeType;
  label: string;
  vertical: Vertical;
  role: StructuralRole;
  /**
   * The complete set of the subtype's own fields. Sending exactly this set (plus
   * the common base fields) lets the backend's Jackson `DEDUCTION` uniquely
   * resolve the node subtype — every set below contains a field, or a
   * combination, that no other subtype knows.
   */
  fields: NodeFieldSpec[];
}

export const NODE_TYPE_SPECS: NodeTypeSpec[] = [
  {
    type: 'WELL',
    label: 'Well',
    vertical: 'EXTRACTION',
    role: 'GENERATOR',
    fields: [
      { key: 'maxCollectionCapacity', label: 'Max collection capacity' },
      { key: 'declineCurve', label: 'Decline curve' },
      { key: 'gasRichness', label: 'Gas richness (0-1)' },
      { key: 'dtmTime', label: 'DTM time', integer: true },
      { key: 'dtmCost', label: 'DTM cost' },
      { key: 'surface', label: 'Surface' },
    ],
  },
  {
    type: 'GATHERING_NETWORK',
    label: 'Gathering network',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTransportCapacity', label: 'Max transport capacity' },
      { key: 'length', label: 'Length' },
      { key: 'lossPerMeter', label: 'Loss per meter' },
      { key: 'connectedWells', label: 'Connected wells', integer: true },
    ],
  },
  {
    type: 'TREATMENT_PLANT',
    label: 'Treatment plant',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTreatmentCapacity', label: 'Max treatment capacity' },
      { key: 'contaminantWaste', label: 'Contaminant waste' },
      { key: 'intermediateStorage', label: 'Intermediate storage' },
      { key: 'treatmentCost', label: 'Treatment cost' },
    ],
  },
  {
    type: 'PIPELINE',
    label: 'Pipeline',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxFlowCapacity', label: 'Max flow capacity' },
      { key: 'length', label: 'Length' },
      { key: 'lossPerKm', label: 'Loss per km' },
    ],
  },
  {
    type: 'COMPRESSING_PLANT',
    label: 'Compressing plant',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxCompressionCapacity', label: 'Max compression capacity' },
      { key: 'processWaste', label: 'Process waste' },
      { key: 'gasConsumption', label: 'Gas consumption' },
    ],
  },
  {
    type: 'GROUND_LIQUEFACTION_PLANT',
    label: 'Ground liquefaction plant',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Max processing capacity' },
      { key: 'mtpaRatio', label: 'MTPA ratio' },
      { key: 'intermediateStorage', label: 'Intermediate storage' },
      { key: 'gasConsumption', label: 'Gas consumption' },
    ],
  },
  {
    type: 'FLNG_UNIT',
    label: 'FLNG unit',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Max processing capacity' },
      { key: 'mtpaRatio', label: 'MTPA ratio' },
      { key: 'intermediateStorage', label: 'Intermediate storage' },
      { key: 'vesselDepth', label: 'Vessel depth' },
      { key: 'hiringCost', label: 'Hiring cost' },
    ],
  },
  {
    type: 'SEAPORT_TERMINAL',
    label: 'Seaport terminal',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'intermediateStorage', label: 'Intermediate storage' },
      { key: 'portDepth', label: 'Port depth' },
      { key: 'shipCapacity', label: 'Ship capacity', integer: true },
    ],
  },
  {
    type: 'LNG_CAMER',
    label: 'LNG carrier',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'exportFrequency', label: 'Export frequency', integer: true },
      { key: 'shipCapacity', label: 'Ship capacity' },
      { key: 'fullLoadTime', label: 'Full load time' },
      { key: 'hiringCost', label: 'Hiring cost' },
      { key: 'timeToDestination', label: 'Time to destination', integer: true },
    ],
  },
];

export function specForType(type: NodeType): NodeTypeSpec | undefined {
  return NODE_TYPE_SPECS.find((s) => s.type === type);
}

/** Palette colour per vertical (from the brand tokens). */
export const VERTICAL_COLORS: Record<Vertical, string> = {
  EXTRACTION: '#6dbe45',
  TRANSPORTATION: '#3f8130',
  LIQUEFACTION: '#55a238',
  EXPORT: '#326626',
  INTERNAL_CONSUMPTION: '#6f767f',
};

export const NODE_STATES: NodeState[] = ['PROPOSED', 'PENDING', 'RUNNING', 'REMOVED'];

/** Base fields every node create/edit payload carries. */
export interface NodeBaseValues {
  name: string;
  state: NodeState;
  upkeepCosts: number;
  operatingCosts: number;
  lifespanInMonths: number;
  maintenanceIntervalInDays: number;
  wastePercentage: number;
}

export function defaultBaseValues(): NodeBaseValues {
  return {
    name: '',
    state: 'PROPOSED',
    upkeepCosts: 0,
    operatingCosts: 0,
    lifespanInMonths: 0,
    maintenanceIntervalInDays: 30,
    wastePercentage: 0,
  };
}

export interface GraphDataInput {
  graphPosition?: { x: number | null; y: number | null } | null;
  geographicalPosition?: { longitude: number | null; latitude: number | null } | null;
}

/**
 * Builds a node payload (create or edit) from the common base fields, the full
 * set of type-specific fields (which drives the backend's type deduction), the
 * graph data and, on edit, the node's existing identity.
 */
export function buildNodePayload(
  spec: NodeTypeSpec,
  base: NodeBaseValues,
  typeFields: Record<string, number>,
  graphData: GraphDataInput,
  identity?: string,
): Record<string, unknown> {
  return {
    name: base.name,
    state: base.state,
    upkeepCosts: base.upkeepCosts,
    operatingCosts: base.operatingCosts,
    lifespanInMonths: base.lifespanInMonths,
    maintenanceIntervalInDays: base.maintenanceIntervalInDays,
    wastePercentage: base.wastePercentage,
    investmentCost: { components: [] },
    graphData,
    type: { vertical: spec.vertical, role: spec.role, nodeType: spec.type },
    ...(identity ? { identity } : {}),
    ...spec.fields.reduce<Record<string, number>>((acc, f) => {
      acc[f.key] = typeFields[f.key] ?? 0;
      return acc;
    }, {}),
  };
}

/**
 * Create payload: places the node at the given canvas position and defaults its
 * real-world position near Argentina (small spread) so it is visible on the map
 * immediately; the user can drag it to its true location.
 */
export function buildCreatePayload(
  spec: NodeTypeSpec,
  base: NodeBaseValues,
  typeFields: Record<string, number>,
  graphX: number,
  graphY: number,
): Record<string, unknown> {
  return buildNodePayload(spec, base, typeFields, {
    graphPosition: { x: graphX, y: graphY },
    geographicalPosition: {
      longitude: -64 + (Math.random() - 0.5) * 6,
      latitude: -38 + (Math.random() - 0.5) * 6,
    },
  });
}
