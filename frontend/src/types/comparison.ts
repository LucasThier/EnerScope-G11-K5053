import type { NumericStatistics } from './economics';

/** Mirrors `org.enerscope.comparison` DTOs. Keep in sync with the backend. */
export type ComparisonMetric =
  | 'NPV' | 'IRR' | 'ROI' | 'ROE' | 'SIMPLE_PAYBACK' | 'DISCOUNTED_PAYBACK'
  | 'CAPEX' | 'OPEX' | 'REVENUE' | 'TAXES' | 'NET_CASH_FLOW'
  | 'PRODUCED_VOLUME' | 'EXPORTED_VOLUME' | 'LOSSES' | 'OPERATING_HOURS' | 'EVENTS';
export type MetricCategory = 'FINANCIAL' | 'COST' | 'CASH_FLOW' | 'PHYSICAL';
export type MetricUnit = 'CURRENCY' | 'FRACTION' | 'YEARS' | 'SIMULATOR_UNIT' | 'HOURS' | 'COUNT';
export type MetricPreference = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL';
export type DifferenceAssessment = 'BASELINE' | 'EQUAL' | 'BETTER' | 'WORSE' | 'CHANGED' | 'NOT_COMPARABLE';
export type FindingType =
  | 'SIGNIFICANT_IMPROVEMENT' | 'SIGNIFICANT_DETERIORATION' | 'SIGNIFICANT_CHANGE' | 'NPV_SIGN_CHANGE'
  | 'AVAILABILITY_CHANGE' | 'NODE_ADDED' | 'NODE_REMOVED' | 'NODE_FLOW_CHANGE';
export type ComparabilityWarning =
  | 'DIFFERENT_CURRENCY' | 'DIFFERENT_START_YEAR' | 'DIFFERENT_HORIZON' | 'DIFFERENT_WACC'
  | 'MIXED_EVALUATION_MODES' | 'DIFFERENT_SIMULATION_VERSION' | 'INCOMPLETE_RESULTS';
export type EvaluationMode = 'DETERMINISTIC' | 'PROBABILISTIC';

export interface ComparisonItem { versionId: string; evaluationId: string | null }

export interface ComparisonRequest {
  name?: string | null;
  description?: string | null;
  relevanceThreshold?: number | null;
  rateThreshold?: number | null;
  items: ComparisonItem[];
}

export interface MetricValue {
  value: number | null;
  status: string;
  availableRounds: number;
  totalRounds: number;
  distribution: NumericStatistics | null;
}

export interface NodeFlow {
  input: number | null; output: number | null; exported: number | null;
  losses: number | null; operatingHours: number | null; events: number | null;
}

export interface ComparedScenario {
  evaluationId: string;
  versionId: string;
  versionName: string;
  evaluatedAt: string;
  baseline: boolean;
  mode: EvaluationMode;
  rounds: number;
  currency: string | null;
  startYear: number | null;
  years: number | null;
  wacc: number | null;
  bestIn: ComparisonMetric[];
}

export interface MetricCell {
  evaluationId: string;
  value: MetricValue;
  absoluteDelta: number | null;
  relativeDelta: number | null;
  assessment: DifferenceAssessment;
  relevant: boolean;
}

export interface MetricRow {
  metric: ComparisonMetric;
  label: string;
  category: MetricCategory;
  unit: MetricUnit;
  preference: MetricPreference;
  bestEvaluationIds: string[];
  relevant: boolean;
  values: MetricCell[];
}

export interface NodeCell {
  evaluationId: string;
  present: boolean;
  flows: NodeFlow | null;
  outputDelta: number | null;
  outputRelativeDelta: number | null;
  relevant: boolean;
}

export interface NodeRow { nodeKey: string; name: string; nodeType: string | null; relevant: boolean; values: NodeCell[] }

export interface SeriesPoint {
  year: number;
  cashFlow: number | null;
  cumulativeCashFlow: number | null;
  producedVolume: number | null;
  exportedVolume: number | null;
}

export interface Series { evaluationId: string; points: SeriesPoint[] }

export interface Warning { code: ComparabilityWarning; message: string; evaluationIds: string[] }

export interface Finding {
  type: FindingType;
  evaluationId: string;
  metric: ComparisonMetric | null;
  nodeKey: string | null;
  relativeDelta: number | null;
  message: string;
}

export interface ComparisonResult {
  baselineEvaluationId: string;
  relevanceThreshold: number;
  rateThreshold: number;
  scenarios: ComparedScenario[];
  metrics: MetricRow[];
  nodes: NodeRow[];
  annual: Series[];
  warnings: Warning[];
  findings: Finding[];
}

export interface ComparisonSummary { id: string; name: string; description: string | null; scenarioCount: number; createdAt: string }

export interface SavedComparison {
  id: string;
  name: string;
  description: string | null;
  relevanceThreshold: number;
  rateThreshold: number;
  createdAt: string;
  items: ComparisonItem[];
  result: ComparisonResult;
}
