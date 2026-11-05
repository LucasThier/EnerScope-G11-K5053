export interface SimulationMetadata { rounds: number; seed: string; algorithm: string; calculationVersion: number }
export interface NumericStatistics { count: number; mean: number | null; min: number | null; max: number | null; p10: number | null; p50: number | null; p90: number | null }
export interface PaybackStatistics { denominator: number; recoveredProbability: number | null; recoveryPeriods: NumericStatistics; statuses: Record<string, number>; becomesNegativeAgainCount: number }
export interface ProbabilisticSummary {
  rounds: number; npv: NumericStatistics; positiveNpvProbability: number | null;
  irr: NumericStatistics; irrStatuses: Record<string, number>;
  simplePayback: PaybackStatistics; discountedPayback: PaybackStatistics;
  cashFlows: { period: number; year: number; values: NumericStatistics }[];
  operational: { nodeId: string; period: number; metric: Exclude<keyof AnnualNodeMetrics, 'nodeId' | 'period'>; values: NumericStatistics }[];
}
export interface EconomicJob {
  id: string; status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'INTERRUPTED';
  rounds: number; completedRounds: number; seed: string; createdAt: string; startedAt: string | null;
  finishedAt: string | null; errorMessage: string | null; evaluationId: string | null;
}
export interface EconomicRound {
  round: number; seed: string; rawMetrics: AnnualNodeMetrics[]; result: Evaluation['snapshot']['result'];
}
export interface EconomicRoundSummary { round: number; seed: string; npv: number; indicators: Indicators }
export interface Scenario {
  id: string; name: string; lastModified: string;
  nodes: { id: string; name: string; type: string }[];
}
export type Driver = 'FIXED' | 'OUTPUT_VOLUME' | 'EXPORTED_VOLUME';
export interface EconomicRule {
  id: string; concept: string; taxEntityId: string | null; counterpartyId: string | null;
  direction: 'INCOME' | 'EXPENSE'; cashClassification: 'CASH' | 'NON_CASH';
  driver: string; unitValue: number; unit: string; currency: string;
  validFrom: string; validTo: string;
  occurrences: { recognitionDate: string; cashDate: string | null }[];
}
export interface CapitalAsset {
  id: string; nodeId: string | null; taxEntityId: string | null; concept: string; currency: string;
  cost: number; residualValue: number; purchaseDate: string; paymentDate: string;
  serviceDate: string; usefulLifeMonths: number;
}
export interface EconomicConfiguration {
  startYear: number; years: number; currency: string; wacc: number;
  taxEntities: { id: string; name: string; jurisdiction: string; taxRate: number; carryLosses: boolean;
    openingLoss: number; lossExpiryYears: number | null; lossOffsetLimit: number; taxPaymentLagYears: number }[];
  boundary: string[];
  nodeProfiles: { nodeId: string; ownership: { taxEntityId: string; share: number }[]; rules: EconomicRule[] }[];
  adjustments: EconomicRule[];
  contracts: { id: string; deliveryNodeId: string; revenueRule: EconomicRule }[];
  assets: CapitalAsset[];
  taxTreatments: { concept: string; taxEntityId: string; validFrom: string; validTo: string;
    classification: string; deductibleFraction: number }[];
  conversions: { nodeId: string; metric: string; rawUnit: string; unit: string; factor: number; annualQuantity: number | null }[];
}
export interface Payback {
  status: 'RECOVERED' | 'NOT_RECOVERED_WITHIN_HORIZON' | 'NOT_APPLICABLE' | 'INSUFFICIENT_DATA' | 'NUMERICAL_FAILURE';
  period: number | null; year: number | null; becomesNegativeAgain: boolean;
}
export interface Indicators {
  irr: { status: 'CALCULATED' | 'NO_SIGN_CHANGE' | 'NON_CONVENTIONAL' | 'NUMERICAL_FAILURE' | 'INSUFFICIENT_DATA'; rate: number | null };
  simplePayback: Payback; discountedPayback: Payback;
  origin: 'STORED' | 'DERIVED_FROM_SNAPSHOT'; calculationVersion: number;
}
export interface EconomicPeriod {
  period: number; year: number; taxableIncome: number; deductibleExpenses: number;
  nonCashExpenses: number; resultBeforeTax: number; taxes: number; resultAfterTax: number;
  nonTaxableIncome: number; nonTaxableExpenses: number; nonCashAdjustments: number;
  cashTimingAdjustment: number; cashFlow: number; discountedCashFlow: number;
}
export interface AnnualNodeMetrics {
  nodeId: string; period: number;
  input: number | null; output: number | null; exported: number | null;
  losses: number | null; operatingHours: number | null; events: number | null;
}
export interface PhysicalNodeSnapshot { id: string; name?: string | null }
export interface Evaluation {
  id: string; createdAt: string;
  snapshot: { simulation?: SimulationMetadata | null; summary?: ProbabilisticSummary | null; schemaVersion: number; rawMetrics?: AnnualNodeMetrics[] | null; physicalNodes?: PhysicalNodeSnapshot[] | null; configuration: EconomicConfiguration | null;
    result: { npv: number | null; indicators: Indicators | null; periods: EconomicPeriod[] | null;
      pendingBalances: unknown[] | null } | null };
}
