import type {
  CostBasis,
  Distribution,
  DistributionType,
  InvestmentComponent,
  NodeState,
  NodeType,
  StructuralRole,
  Vertical,
} from '../../types/diagram';

/** A numeric, type-specific field of a node subtype. */
export interface NodeFieldSpec {
  key: string;
  label: string;
  /** Sent as an integer (backend `int`) rather than a float. */
  integer?: boolean;
  /** Value a new node starts with; 0 when absent. */
  defaultValue?: number;
  /** Upper bound the backend enforces; the form refuses values above it. */
  max?: number;
}

export interface NodeTypeSpec {
  type: NodeType;
  label: string;
  vertical: Vertical;
  role: StructuralRole;
  /**
   * The subtype's own numeric fields. The backend picks the node subtype from
   * `type.nodeType`, so these only need to be the values the subtype stores —
   * including every one the simulator reads, or the node simulates with zeros.
   */
  fields: NodeFieldSpec[];
  /** Wells carry a decline curve (a distribution), edited apart from the numeric fields. */
  hasDeclineCurve?: boolean;
}

/**
 * Capacities and consumptions are per hour: the engine steps hour by hour, so a
 * node's capacity is what it can handle in one simulated hour.
 */
export const NODE_TYPE_SPECS: NodeTypeSpec[] = [
  {
    type: 'WELL',
    label: 'Pozo',
    vertical: 'EXTRACTION',
    role: 'GENERATOR',
    hasDeclineCurve: true,
    fields: [
      { key: 'maxCollectionCapacity', label: 'Capacidad de extracción (por hora)', defaultValue: 100 },
      // Stored as a 0–1 ratio (the entity validates it), and read by the engine
      // as the share of contaminants the treatment plant removes.
      { key: 'gasRichness', label: 'Riqueza / contaminantes (0 a 1)', max: 1 },
      { key: 'dtmTime', label: 'Tiempo DTM (horas)', integer: true, defaultValue: 24 },
      { key: 'dtmCost', label: 'Costo DTM' },
      { key: 'surface', label: 'Superficie (km²)' },
    ],
  },
  {
    type: 'GATHERING_NETWORK',
    label: 'Red de recolección',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTransportCapacity', label: 'Capacidad de transporte (por hora)', defaultValue: 100 },
      { key: 'length', label: 'Longitud (m)' },
      { key: 'lossPerMeter', label: 'Pérdida por metro (%)' },
      { key: 'connectedWells', label: 'Pozos conectados', integer: true },
    ],
  },
  {
    type: 'TREATMENT_PLANT',
    label: 'Planta de tratamiento',
    vertical: 'EXTRACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxTreatmentCapacity', label: 'Capacidad de tratamiento (por hora)', defaultValue: 100 },
      { key: 'contaminantWaste', label: 'Residuo de contaminantes' },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio' },
      { key: 'treatmentCost', label: 'Costo de tratamiento' },
    ],
  },
  {
    type: 'PIPELINE',
    label: 'Gasoducto',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxFlowCapacity', label: 'Caudal máximo (por hora)', defaultValue: 100 },
      { key: 'length', label: 'Longitud (km)' },
      { key: 'lossPerKm', label: 'Pérdida por km (%)' },
    ],
  },
  {
    type: 'COMPRESSING_PLANT',
    label: 'Planta compresora',
    vertical: 'TRANSPORTATION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxCompressionCapacity', label: 'Capacidad de compresión (por hora)', defaultValue: 100 },
      { key: 'processWaste', label: 'Merma de proceso (%)' },
      { key: 'gasConsumption', label: 'Consumo de gas (%)' },
    ],
  },
  {
    type: 'GROUND_LIQUEFACTION_PLANT',
    label: 'Planta de licuefacción',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Capacidad de proceso (por hora)', defaultValue: 100 },
      { key: 'mtpaRatio', label: 'Rendimiento GNL (%)', defaultValue: 90 },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio', defaultValue: 1000 },
      { key: 'gasConsumption', label: 'Consumo de gas (%)' },
    ],
  },
  {
    type: 'FLNG_UNIT',
    label: 'Unidad FLNG',
    vertical: 'LIQUEFACTION',
    role: 'INTERMEDIATE',
    fields: [
      { key: 'maxProcessingCapacity', label: 'Capacidad de proceso (por hora)', defaultValue: 100 },
      { key: 'mtpaRatio', label: 'Rendimiento GNL (%)', defaultValue: 90 },
      { key: 'intermediateStorage', label: 'Almacenamiento intermedio', defaultValue: 1000 },
      { key: 'gasConsumption', label: 'Consumo de gas (%)' },
      { key: 'shipCapacity', label: 'Buques en simultáneo', integer: true, defaultValue: 1 },
      { key: 'vesselDepth', label: 'Calado (m)' },
      { key: 'hiringCost', label: 'Costo de contratación' },
    ],
  },
  {
    type: 'SEAPORT_TERMINAL',
    label: 'Terminal portuaria',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'intermediateStorage', label: 'Almacenamiento', defaultValue: 1000 },
      { key: 'portDepth', label: 'Profundidad (m)' },
      { key: 'shipCapacity', label: 'Buques en simultáneo', integer: true, defaultValue: 1 },
    ],
  },
  {
    type: 'LNG_CAMER',
    label: 'Buque metanero',
    vertical: 'EXPORT',
    role: 'SINK',
    fields: [
      { key: 'shipCapacity', label: 'Capacidad de carga', defaultValue: 1000 },
      { key: 'fullLoadTime', label: 'Tiempo de carga (horas)', defaultValue: 24 },
      { key: 'timeToDestination', label: 'Tiempo de viaje (horas)', integer: true, defaultValue: 240 },
      { key: 'exportFrequency', label: 'Espera entre viajes (horas)', integer: true },
      { key: 'hiringCost', label: 'Costo de contratación' },
    ],
  },
  {
    type: 'INTERNAL_CONSUMPTION',
    label: 'Consumo interno',
    vertical: 'INTERNAL_CONSUMPTION',
    role: 'SINK',
    fields: [
      { key: 'consumptionSummer', label: 'Demanda verano (por hora)' },
      { key: 'consumptionAutumn', label: 'Demanda otoño (por hora)' },
      { key: 'consumptionWinter', label: 'Demanda invierno (por hora)' },
      { key: 'consumptionSpring', label: 'Demanda primavera (por hora)' },
    ],
  },
  {
    type: 'INDUSTRIAL_CONSUMPTION',
    label: 'Consumo industrial',
    vertical: 'INTERNAL_CONSUMPTION',
    role: 'SINK',
    fields: [{ key: 'consumption', label: 'Demanda (por hora)' }],
  },
];

export function specForType(type: NodeType): NodeTypeSpec | undefined {
  return NODE_TYPE_SPECS.find((s) => s.type === type);
}

/** Spanish names for the value-chain verticals. */
export const VERTICAL_LABELS: Record<string, string> = {
  EXTRACTION: 'Extracción',
  TRANSPORTATION: 'Transporte',
  LIQUEFACTION: 'Licuefacción',
  EXPORT: 'Exportación',
  INTERNAL_CONSUMPTION: 'Mercado interno',
  UNKNOWN: 'Sin vertical',
};

export const NODE_STATE_LABELS: Record<NodeState, string> = {
  RUNNING: 'En operación',
  PROPOSED: 'Propuesto',
  PENDING: 'Pendiente',
  REMOVED: 'Eliminado',
};

/** The Spanish label of a node type, falling back to the raw enum. */
export function nodeTypeLabel(type: string | null | undefined): string {
  if (!type) return '—';
  return specForType(type as NodeType)?.label ?? type;
}

/** Palette colour per vertical (from the brand tokens). */
export const VERTICAL_COLORS: Record<Vertical, string> = {
  EXTRACTION: '#6dbe45',
  TRANSPORTATION: '#3f8130',
  LIQUEFACTION: '#55a238',
  EXPORT: '#326626',
  INTERNAL_CONSUMPTION: '#6f767f',
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
 * Treated gas can also leave the chain for the domestic market (internal or
 * industrial consumption) from the treatment plant or the transport network.
 */
export const ALLOWED_CONNECTIONS: Partial<Record<NodeType, NodeType[]>> = {
  WELL: ['GATHERING_NETWORK'],
  GATHERING_NETWORK: ['TREATMENT_PLANT'],
  TREATMENT_PLANT: ['PIPELINE', 'COMPRESSING_PLANT', 'INTERNAL_CONSUMPTION', 'INDUSTRIAL_CONSUMPTION'],
  PIPELINE: [
    'PIPELINE',
    'COMPRESSING_PLANT',
    'GROUND_LIQUEFACTION_PLANT',
    'FLNG_UNIT',
    'INTERNAL_CONSUMPTION',
    'INDUSTRIAL_CONSUMPTION',
  ],
  COMPRESSING_PLANT: [
    'PIPELINE',
    'GROUND_LIQUEFACTION_PLANT',
    'FLNG_UNIT',
    'INTERNAL_CONSUMPTION',
    'INDUSTRIAL_CONSUMPTION',
  ],
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
  state: NodeState;
  /** Cost of one maintenance stop. */
  upkeepCosts: number;
  /** Monthly operating cost. */
  operatingCosts: number;
  lifespanInMonths: number;
  maintenanceIntervalInDays: number;
  /** Hours a maintenance stop lasts. */
  maintenanceDuration: number;
  wastePercentage: number;
}

/**
 * Defaults a new node can be simulated with as it is: a node with a zero
 * lifespan stops at hour zero, and a zero maintenance interval keeps it in
 * maintenance every hour, so neither default may be 0.
 */
export function defaultBaseValues(): NodeBaseValues {
  return {
    name: '',
    state: 'PROPOSED',
    upkeepCosts: 0,
    operatingCosts: 0,
    lifespanInMonths: 240,
    maintenanceIntervalInDays: 365,
    maintenanceDuration: 24,
    wastePercentage: 0,
  };
}

/** The type-specific defaults of a new node. */
export function defaultTypeFields(spec: NodeTypeSpec | undefined): Record<string, number> {
  const values: Record<string, number> = {};
  spec?.fields.forEach((field) => {
    values[field.key] = field.defaultValue ?? 0;
  });
  return values;
}

export const DISTRIBUTION_LABELS: Record<DistributionType, string> = {
  CONSTANT: 'Constante',
  UNIFORM: 'Uniforme',
  NORMAL: 'Normal',
};

/** Names of a distribution's two parameters, by type. */
export const DISTRIBUTION_PARAMETERS: Record<DistributionType, [string, string | null]> = {
  CONSTANT: ['Valor', null],
  UNIFORM: ['Mínimo', 'Máximo'],
  NORMAL: ['Media', 'Desvío estándar'],
};

export function defaultDeclineCurve(): Distribution {
  return { type: 'CONSTANT', val_a: 5, val_b: null };
}

/** Why a distribution cannot be sent, or null when it is valid. */
export function distributionError(curve: Distribution): string | null {
  if (!Number.isFinite(curve.val_a)) return 'Completá el primer parámetro.';
  if (curve.type === 'UNIFORM' && !(curve.val_b != null && curve.val_b > curve.val_a)) {
    return 'El máximo tiene que ser mayor que el mínimo.';
  }
  if (curve.type === 'NORMAL' && !(curve.val_b != null && curve.val_b > 0)) {
    return 'El desvío estándar tiene que ser mayor que 0.';
  }
  return null;
}

export const COST_BASIS_LABELS: Record<CostBasis, string> = {
  FLAT: 'Monto fijo',
  PER_M: 'Por metro (red de recolección)',
  PER_KM: 'Por km (gasoducto)',
  PER_KM2: 'Por km² (pozo)',
  PER_CONECTIONS_TOTAL: 'Por conexión',
  PER_CONECTIONS_INPUT: 'Por conexión de entrada',
  PER_CONECTIONES_OUTPUT: 'Por conexión de salida',
};

/** The bases the cost engine actually computes; the other two always cost 0. */
export const COST_BASES: CostBasis[] = ['FLAT', 'PER_M', 'PER_KM', 'PER_KM2', 'PER_CONECTIONS_TOTAL'];

export interface GraphDataInput {
  graphPosition?: { x: number | null; y: number | null } | null;
  geographicalPosition?: { longitude: number | null; latitude: number | null } | null;
}

/** What a node payload carries beyond the base and numeric type fields. */
export interface NodeExtras {
  declineCurve?: Distribution | null;
  investmentCost: InvestmentComponent[];
}

/**
 * Builds a node payload (create or edit) from the common base fields, the
 * type-specific fields, the investment components and decline curve, the graph
 * data and, on edit, the node's existing identity.
 */
export function buildNodePayload(
  spec: NodeTypeSpec,
  base: NodeBaseValues,
  typeFields: Record<string, number>,
  extras: NodeExtras,
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
    wastePercentage: base.wastePercentage,
    investmentCost: {
      components: extras.investmentCost
        .filter((component) => component.name.trim())
        .map((component) => ({ ...component, name: component.name.trim() })),
    },
    graphData,
    type: { vertical: spec.vertical, role: spec.role, nodeType: spec.type },
    ...(identity ? { identity } : {}),
    ...(spec.hasDeclineCurve ? { declineCurve: extras.declineCurve ?? null } : {}),
    ...spec.fields.reduce<Record<string, number>>((acc, f) => {
      const value = typeFields[f.key] ?? 0;
      acc[f.key] = f.integer ? Math.round(value) : value;
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
  extras: NodeExtras,
  graphX: number,
  graphY: number,
): Record<string, unknown> {
  return buildNodePayload(spec, base, typeFields, extras, {
    graphPosition: { x: graphX, y: graphY },
    geographicalPosition: {
      longitude: -64 + (Math.random() - 0.5) * 6,
      latitude: -38 + (Math.random() - 0.5) * 6,
    },
  });
}
