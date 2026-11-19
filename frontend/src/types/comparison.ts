import type { NumericStatistics } from './economics';

/** Mirrors `org.enerscope.comparison` DTOs. Keep in sync with the backend. */
export type ComparisonMetric =
  | 'NPV' | 'IRR' | 'ROI' | 'ROE' | 'SIMPLE_PAYBACK' | 'DISCOUNTED_PAYBACK'
  | 'CAPEX' | 'OPEX' | 'REVENUE' | 'TAXES' | 'NET_CASH_FLOW'
  | 'PRODUCED_VOLUME' | 'EXPORTED_VOLUME' | 'LOSSES' | 'OPERATING_HOURS' | 'EVENTS';
export type MetricCategory = 'FINANCIAL' | 'COST' | 'CASH_FLOW' | 'PHYSICAL';
export type MetricUnit = 'CURRENCY' | 'FRACTION' | 'YEARS' | 'SIMULATOR_UNIT' | 'HOURS' | 'COUNT';
export type MetricPreference = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER' | 'NEUTRAL';
/** Where a value stands among all compared scenarios. There is no reference scenario. */
export type Standing = 'BEST' | 'MIDDLE' | 'WORST' | 'TIED' | 'UNRANKED' | 'NOT_COMPARABLE';
export type FindingType = 'NPV_SIGN_SPLIT' | 'AVAILABILITY_SPLIT' | 'LARGE_GAP' | 'NODE_PARTIAL' | 'NODE_FLOW_GAP';
export type ComparabilityWarning =
  | 'DIFFERENT_CURRENCY' | 'DIFFERENT_START_YEAR' | 'DIFFERENT_HORIZON' | 'DIFFERENT_WACC'
  | 'MIXED_EVALUATION_MODES' | 'DIFFERENT_SIMULATION_VERSION' | 'INCOMPLETE_RESULTS';
export type EvaluationMode = 'DETERMINISTIC' | 'PROBABILISTIC';

export type ScenarioStatus = 'UP_TO_DATE' | 'OUTDATED' | 'NEVER_SIMULATED' | 'SIMULATING';

/** A scenario as the picker shows it (`GET /projects/{id}/comparisons/scenarios`). */
export interface ComparableScenario {
  projectId: string;
  projectName: string;
  versionId: string;
  versionName: string;
  status: ScenarioStatus;
  latestEvaluationId: string | null;
  latestEvaluatedAt: string | null;
  mode: EvaluationMode | null;
  rounds: number | null;
  ready: boolean;
  readinessErrors: string[];
  canSimulate: boolean;
  job: { id: string; status: string; completedRounds: number; rounds: number; errorMessage: string | null } | null;
}

/** A compared scenario: always through its latest, up-to-date simulation. */
export interface ComparisonItem { versionId: string }

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
  projectId: string;
  projectName: string;
  versionId: string;
  versionName: string;
  evaluatedAt: string;
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
  /** 1 = best; equal values share a rank; null when unranked. */
  rank: number | null;
  /** Distance to the best value (≥ 0) in the indicator's unit; null when unranked. */
  gapToBest: number | null;
  relativeGapToBest: number | null;
  standing: Standing;
}

export interface MetricRow {
  metric: ComparisonMetric;
  label: string;
  category: MetricCategory;
  unit: MetricUnit;
  preference: MetricPreference;
  bestEvaluationIds: string[];
  worstEvaluationIds: string[];
  relevant: boolean;
  values: MetricCell[];
}

export interface NodeCell {
  evaluationId: string;
  present: boolean;
  flows: NodeFlow | null;
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
  evaluationIds: string[];
  metric: ComparisonMetric | null;
  nodeKey: string | null;
  relativeGap: number | null;
  message: string;
}

export interface ComparisonResult {
  relevanceThreshold: number;
  rateThreshold: number;
  scenarios: ComparedScenario[];
  metrics: MetricRow[];
  nodes: NodeRow[];
  annual: Series[];
  warnings: Warning[];
  findings: Finding[];
}

export interface ComparisonSummary { id: string; name: string; description: string | null; scenarios: string[]; createdAt: string }

export interface SavedComparison {
  id: string;
  name: string;
  description: string | null;
  relevanceThreshold: number;
  rateThreshold: number;
  createdAt: string;
  items: { versionId: string; evaluationId: string }[];
  result: ComparisonResult;
}
