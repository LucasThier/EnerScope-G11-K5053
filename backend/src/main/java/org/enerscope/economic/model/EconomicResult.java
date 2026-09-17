package org.enerscope.economic.model;

import java.math.BigDecimal;
import java.util.List;

public record EconomicResult(List<EconomicEntry> entries, List<OperationalMetric> operationalMetrics,
        List<PeriodEconomicResult> periods, List<EntityTaxResult> entityTaxes,
        List<PendingBalance> pendingBalances, BigDecimal npv) {
    public record PeriodEconomicResult(int period, int year, BigDecimal taxableIncome,
            BigDecimal deductibleExpenses, BigDecimal nonCashExpenses, BigDecimal resultBeforeTax,
            BigDecimal taxes, BigDecimal resultAfterTax, BigDecimal nonTaxableIncome,
            BigDecimal nonTaxableExpenses, BigDecimal nonCashAdjustments, BigDecimal cashTimingAdjustment,
            BigDecimal cashFlow, BigDecimal discountedCashFlow) {}
    public record EntityTaxResult(String taxEntityId, int year, BigDecimal resultBeforeTax,
            BigDecimal lossesUsed, BigDecimal lossesExpired, BigDecimal closingLosses,
            BigDecimal taxableBase, BigDecimal tax, int paymentYear) {}
    public record PendingBalance(String sourceId, String taxEntityId, String kind,
            BigDecimal amount, java.time.LocalDate dueDate) {}
}
