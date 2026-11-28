import type { ResolvedTheme } from '../../hooks/useTheme';
import type { DeclineDistribution, NodeState, NodeType, StructuralRole, Vertical } from '../../types/diagram';

/** A numeric, type-specific field of a node subtype. */
export interface NodeFieldSpec {
  key: string;
  label: string;
  /** Sent as an integer (backend `int`) rather than a float. */
  integer?: boolean;
  /** Legacy API field, retained in payloads but excluded from operational panels. */
  economic?: boolean;
}

export interface NodeTypeSpec {
  type: NodeType;
  label: string;
  vertical: Vertical;
  role: StructuralRole;
  /**
   * The complete set of the subtype's own fields. The backend chooses the DTO
   * using the explicit type.nodeType discriminator sent in the payload.
   */
  fields: NodeFieldSpec[];
}

export const NODE_TYPE_SPECS: NodeTypeSpec[] = [
  {
    type: 'WELL',
    label: 'Pozo',
    vertical: 'EXTRACTION',
    role: 'GENERATOR',
    fields: [
      { key: 'maxCollectionCapacity', label: 'Capacidad máxima de recolección' },
      { key: 'declineCurve', label: 'Curva de declinación' },
      { key: 'gasRichness', label: 'Riqueza del gas (0–1)' },
      { key: 'dtmTime', label: 'Tiempo DTM', integer: true },
      { key: 'dtmCost', label: 'Costo DTM', economic: true },
      { key: 'surface', label: 'Superficie' },
    ],
  },
  {
    type: 'GATHERING_NETWORK',
    label: 'Red de recolección',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTransportCapacity', label: 'Capacidad máxima de transporte' },
      { key: 'length', label: 'Longitud' },
      { key: 'lossPerMeter', label: 'Pérdidas por metro' },
      { key: 'connectedWells', label: 'Pozos conectados', integer: true },
    ],
  },
  {
    type: 'TREATMENT_PLANT',
    label: 'Planta de tratamiento',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTreatmentCapacity', label: 'Capacidad de tratamiento' },
      { key: 'contaminantWaste', label: 'Residuos contaminantes' },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio' },
      { key: 'treatmentCost', label: 'Costo de tratamiento', economic: true },
    ],
  },
  {
    type: 'PIPELINE',
    label: 'Gasoducto',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxFlowCapacity', label: 'Capacidad máxima de flujo' },
      { key: 'length', label: 'Longitud' },
      { key: 'lossPerKm', label: 'Pérdidas por kilómetro' },
    ],
  },
  {
    type: 'PIPELINE_CONECTION',
    label: 'Conexión de gasoducto',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'transferCapacity', label: 'Capacidad de transferencia' },
      { key: 'outputPriority', label: 'Prioridad de salida' },
    ],
  },
  {
    type: 'COMPRESSING_PLANT',
    label: 'Planta compresora',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxCompressionCapacity', label: 'Capacidad de compresión' },
      { key: 'processWaste', label: 'Pérdidas del proceso' },
      { key: 'gasConsumption', label: 'Consumo de gas' },
    ],
  },
  {
    type: 'GROUND_LIQUEFACTION_PLANT',
    label: 'Planta de licuefacción en tierra',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Capacidad de procesamiento' },
      { key: 'mtpaRatio', label: 'Relación MTPA' },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio' },
      { key: 'gasConsumption', label: 'Consumo de gas' },
    ],
  },
  {
    type: 'FLNG_UNIT',
    label: 'Unidad FLNG',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Capacidad de procesamiento' },
      { key: 'mtpaRatio', label: 'Relación MTPA' },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio' },
      { key: 'vesselDepth', label: 'Calado' },
      { key: 'shipCapacity', label: 'Capacidad del buque', integer: true },
      { key: 'gasConsumption', label: 'Consumo de gas' },
      { key: 'hiringCost', label: 'Costo de contratación', economic: true },
    ],
  },
  {
    type: 'SEAPORT_TERMINAL',
    label: 'Terminal portuaria',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio' },
      { key: 'portDepth', label: 'Profundidad del puerto' },
      { key: 'shipCapacity', label: 'Capacidad del buque', integer: true },
    ],
  },
  {
    type: 'LNG_CAMER',
    label: 'Buque metanero',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'exportFrequency', label: 'Frecuencia de exportación', integer: true },
      { key: 'shipCapacity', label: 'Capacidad del buque' },
      { key: 'fullLoadTime', label: 'Tiempo de carga completa' },
      { key: 'hiringCost', label: 'Costo de contratación', economic: true },
      { key: 'timeToDestination', label: 'Tiempo al destino', integer: true },
    ],
  },
  { type: 'INTERNAL_CONSUMPTION', label: 'Consumo interno', vertical: 'INTERNAL_CONSUMPTION', role: 'SINK',
    fields: [
      { key: 'consumptionSummer', label: 'Consumo de verano' },
      { key: 'consumptionAutumn', label: 'Consumo de otoño' },
      { key: 'consumptionWinter', label: 'Consumo de invierno' },
      { key: 'consumptionSpring', label: 'Consumo de primavera' },
    ],
  },
  { type: 'INDUSTRIAL_CONSUMPTION', label: 'Consumo industrial', vertical: 'INTERNAL_CONSUMPTION', role: 'SINK',
    fields: [{ key: 'consumption', label: 'Consumo constante' }],
  },
];

/** Fields editable in the physical diagram and map. */
export function operationalFields(spec: NodeTypeSpec): NodeFieldSpec[] {
  return spec.fields.filter(field => !field.economic);
}

export function specForType(type: NodeType): NodeTypeSpec | undefined {
  return NODE_TYPE_SPECS.find((s) => s.type === type);
}

export function nodeTypeLabel(type: NodeType): string {
  return specForType(type)?.label ?? type;
}

export const NODE_STATE_LABELS: Record<NodeState, string> = {
  RUNNING: 'En operación',
  PROPOSED: 'Propuesto',
  PENDING: 'Pendiente',
  REMOVED: 'Eliminado',
};

export const VERTICAL_LABELS: Record<Vertical, string> = {
  EXTRACTION: 'Extracción',
  TRANSPORTATION: 'Transporte',
  LIQUEFACTION: 'Licuefacción',
  EXPORT: 'Exportación',
  INTERNAL_CONSUMPTION: 'Consumo interno',
};

export const ROLE_LABELS: Record<StructuralRole, string> = {
  GENERATOR: 'Origen',
  INTERMEDIATE: 'Intermedio',
  SINK: 'Destino',
};

export const DECLINE_LABELS: Record<DeclineDistribution['type'], string> = {
  CONSTANT: 'Constante',
  NORMAL: 'Normal',
  UNIFORM: 'Uniforme',
};

/** Palette colour per vertical (from the brand tokens). */
export const VERTICAL_COLORS: Record<ResolvedTheme, Record<Vertical, string>> = {
  light: {
    EXTRACTION: '#6dbe45',
    TRANSPORTATION: '#3f8130',
    LIQUEFACTION: '#55a238',
    EXPORT: '#326626',
    INTERNAL_CONSUMPTION: '#6f767f',
  },
  dark: {
    EXTRACTION: '#8fd86b',
    TRANSPORTATION: '#71bd5e',
    LIQUEFACTION: '#80c968',
    EXPORT: '#63b05a',
    INTERNAL_CONSUMPTION: '#9aa5b0',
  },
};

export const FALLBACK_VERTICAL_COLOR: Record<ResolvedTheme, string> = {
  light: '#6f767f',
  dark: '#9aa5b0',
};

export function verticalColor(theme: ResolvedTheme, vertical: Vertical | undefined): string {
  return (
    (vertical ? VERTICAL_COLORS[theme][vertical] : undefined) ?? FALLBACK_VERTICAL_COLOR[theme]
  );
}

export const CONNECTION_COLORS: Record<ResolvedTheme, string> = {
  light: '#4b515a',
  dark: '#93a1b0',
};

/**
 * States a user may pick manually. `REMOVED` is intentionally absent: a node
 * only becomes `REMOVED` as the result of deleting it, never as a manual choice
 * (see {@link ../../hooks/useDiagram removeNode}).
 */
export const NODE_STATES: NodeState[] = ['PROPOSED', 'PENDING', 'RUNNING'];

/**
 * Allowed connection targets per node type. Encodes the real-world gas value
 * chain so the diagram only accepts physically meaningful edges, in order:
 * well → gathering → treatment → pipeline/compression → liquefaction (ground or
 * FLNG) → export (seaport terminal → carrier, or FLNG straight to carrier).
 */
const PIPELINE_TARGETS: NodeType[] = [
  'PIPELINE',
  'COMPRESSING_PLANT',
  'GROUND_LIQUEFACTION_PLANT',
  'FLNG_UNIT',
  'INTERNAL_CONSUMPTION',
  'INDUSTRIAL_CONSUMPTION',
];

export const ALLOWED_CONNECTIONS: Partial<Record<NodeType, NodeType[]>> = {
  WELL: ['GATHERING_NETWORK'],
  GATHERING_NETWORK: ['TREATMENT_PLANT'],
  TREATMENT_PLANT: ['PIPELINE', 'COMPRESSING_PLANT', 'INTERNAL_CONSUMPTION', 'INDUSTRIAL_CONSUMPTION'],
  PIPELINE: [...PIPELINE_TARGETS, 'PIPELINE_CONECTION'],
  // regla confirmada por el equipo: va siempre después de un PIPELINE y sale igual que un PIPELINE
  PIPELINE_CONECTION: [...PIPELINE_TARGETS],
  COMPRESSING_PLANT: ['PIPELINE', 'GROUND_LIQUEFACTION_PLANT', 'FLNG_UNIT', 'INTERNAL_CONSUMPTION', 'INDUSTRIAL_CONSUMPTION'],
  GROUND_LIQUEFACTION_PLANT: ['SEAPORT_TERMINAL'],
  FLNG_UNIT: ['LNG_CAMER'],
  SEAPORT_TERMINAL: ['LNG_CAMER'],
  LNG_CAMER: [],
  INTERNAL_CONSUMPTION: [],
  INDUSTRIAL_CONSUMPTION: [],
};

/** The node types a node of `from` type may connect to. */
export function allowedTargetTypes(from: NodeType): NodeType[] {
  return ALLOWED_CONNECTIONS[from] ?? [];
}

/** Whether a connection from a `from`-type node to a `to`-type node is allowed. */
export function canConnect(from: NodeType, to: NodeType): boolean {
  return allowedTargetTypes(from).includes(to);
}

/** Base fields every node create/edit payload carries. */
export interface NodeBaseValues {
  name: string;
  declineCurve?: import("../../types/diagram").DeclineDistribution;
  state: NodeState;
  upkeepCosts: number;
  operatingCosts: number;
  lifespanInMonths: number;
  maintenanceIntervalInDays: number;
  /** Hours. */
  maintenanceDuration: number;
  /** ISO instant as loaded from the node; sent back untouched so an edit never erases it. */
  startupDate?: string | null;
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
    maintenanceDuration: 0,
    wastePercentage: 0,
  };
}

/** The edit form's base values from a loaded node: every stored value round-trips, startup date included. */
export function baseValuesFromDetail(detail: import('../../types/diagram').NodeDetail): NodeBaseValues {
  return {
    name: detail.name,
    declineCurve: detail.declineCurve ?? undefined,
    state: detail.state,
    upkeepCosts: detail.upkeepCosts,
    operatingCosts: detail.operatingCosts,
    lifespanInMonths: detail.lifespanInMonths,
    maintenanceIntervalInDays: detail.maintenanceIntervalInDays,
    maintenanceDuration: detail.maintenanceDuration ?? 0,
    startupDate: detail.startupDate ?? null,
    wastePercentage: detail.wastePercentage,
  };
}

export interface GraphDataInput {
  graphPosition?: { x: number | null; y: number | null } | null;
  geographicalPosition?: { longitude: number | null; latitude: number | null } | null;
}

/**
 * Builds a node payload (create or edit) from the common base fields, the full
 * set of type-specific fields, the explicit node type, the
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
    maintenanceDuration: base.maintenanceDuration,
    // Absent (not null) when there is none: the backend keeps the stored value when the field is missing.
    ...(base.startupDate ? { startupDate: base.startupDate } : {}),
    wastePercentage: base.wastePercentage,
    investmentCost: { components: [] },
    graphData,
    type: { vertical: spec.vertical, role: spec.role, nodeType: spec.type },
    ...(identity ? { identity } : {}),
    ...spec.fields.reduce<Record<string, number>>((acc, f) => {
      acc[f.key] = typeFields[f.key] ?? 0;
      return acc;
    }, {}),
    ...(spec.type === 'WELL' ? { declineCurve: base.declineCurve ?? { type: 'CONSTANT', val_a: typeFields.declineCurve ?? 0, val_b: null } } : {}),
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
