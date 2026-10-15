import type { SimulationWarning } from '../../types/simulation';

/** What each warning code from the backend means, in the user's words. */
export const WARNING_MESSAGES: Record<SimulationWarning, string> = {
  UNSIMULATED_NODES:
    'Algunos nodos forman un ciclo y no se pudieron ordenar: no se simularon y no produjeron nada. Revisá las conexiones.',
  NO_SINKS:
    'El modelo no tiene buques ni consumos: nada cuenta como entregado. Conectá la cadena hasta un buque metanero o un nodo de consumo.',
  MISSING_SIMULATIONS:
    'Hay escenarios sin simular: sus indicadores aparecen vacíos hasta que los simules.',
  DIFFERENT_HORIZONS:
    'Las últimas simulaciones cubren horizontes distintos, así que sus totales no son directamente comparables. Simulá todos con el mismo horizonte.',
};

/** Field names of a node, as the comparison reports them, in Spanish. */
export const FIELD_LABELS: Record<string, string> = {
  name: 'nombre',
  state: 'estado',
  startupDate: 'fecha de puesta en marcha',
  lifespanInMonths: 'vida útil',
  upkeepCosts: 'costo de mantenimiento',
  maintenanceIntervalInDays: 'intervalo de mantenimiento',
  maintenanceDuration: 'duración del mantenimiento',
  operatingCosts: 'costo de operación',
  wastePercentage: 'merma',
  type: 'tipo',
  investmentCost: 'inversión',
  maxCollectionCapacity: 'capacidad de extracción',
  declineCurve: 'curva de declinación',
  gasRichness: 'riqueza de gas',
  DTMTime: 'tiempo DTM',
  DTMCost: 'costo DTM',
  surface: 'superficie',
  maxTransportCapacity: 'capacidad de transporte',
  length: 'longitud',
  lossPerMeter: 'pérdida por metro',
  connectedWells: 'pozos conectados',
  maxTreatmentCapacity: 'capacidad de tratamiento',
  intermediateStorage: 'almacenamiento',
  treatmentCost: 'costo de tratamiento',
  maxFlowCapacity: 'caudal máximo',
  lossPerKm: 'pérdida por km',
  maxCompressionCapacity: 'capacidad de compresión',
  processWaste: 'merma de proceso',
  gasConsumption: 'consumo de gas',
  maxProcessingCapacity: 'capacidad de proceso',
  MTPARatio: 'rendimiento GNL',
  vesselDepth: 'calado',
  hiringCost: 'costo de contratación',
  shipCapacity: 'capacidad de buques',
  portDepth: 'profundidad',
  exportFrequency: 'espera entre viajes',
  fullLoadTime: 'tiempo de carga',
  timeToDestination: 'tiempo de viaje',
  consumptionSummer: 'demanda de verano',
  consumptionAutumn: 'demanda de otoño',
  consumptionWinter: 'demanda de invierno',
  consumptionSpring: 'demanda de primavera',
  consumption: 'demanda',
};

export function fieldLabel(field: string): string {
  return FIELD_LABELS[field] ?? field;
}
