export type ComparisonMetric = 'NPV' | 'IRR' | 'PAYBACK' | 'INVESTMENT' | 'OPERATING_COST' | 'DELIVERED_VOLUME';
export interface ComparisonContext { id: string; name: string; probability: number }
export interface ComparisonCell { contextId: string; versionId: string; evaluationId: string; deliveryNodeIds: string[] }
export interface ComparisonAlternative { id: string; name: string; cells: ComparisonCell[] }
export interface ComparisonCriterion { metric: ComparisonMetric; weight: number; unfavorable: number; favorable: number; minimum: number | null; maximum: number | null }
export interface ComparisonRequest {
  name: string; scopeDescription: string; assumptionsConfirmed: boolean;
  contexts: ComparisonContext[]; alternatives: ComparisonAlternative[]; criteria: ComparisonCriterion[];
}
export interface ComparisonValue { metric: ComparisonMetric; value: number | null; unit: string; score: number | null; contribution: number | null; issue: string | null; meetsLimits: boolean }
export interface ComparisonContextResult { contextId: string; versionId: string; evaluationId: string; rounds: number; values: ComparisonValue[]; score: number | null; eligible: boolean; issues: string[] }
export interface ComparisonAlternativeResult { id: string; name: string; contexts: ComparisonContextResult[]; score: number | null; worstScore: number | null; eligible: boolean; rank: number | null }
export interface ComparisonResult { calculationVersion: number; status: 'READY' | 'BLOCKED' | 'NO_ELIGIBLE_ALTERNATIVE'; currency: string; startYear: number; years: number; alternatives: ComparisonAlternativeResult[]; winnerIds: string[]; issues: string[] }
export interface SavedComparison { id: string; createdAt: string; configuration: ComparisonRequest; result: ComparisonResult }
export interface ComparisonSummary { id: string; createdAt: string; name: string }
