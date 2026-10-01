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
export interface Evaluation {
  id: string; createdAt: string;
  snapshot: { schemaVersion: number; configuration: EconomicConfiguration | null;
    result: { npv: number | null; indicators: Indicators | null; periods: EconomicPeriod[] | null;
      pendingBalances: unknown[] | null } | null };
}
