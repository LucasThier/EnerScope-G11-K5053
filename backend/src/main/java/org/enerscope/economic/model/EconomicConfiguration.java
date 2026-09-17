package org.enerscope.economic.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Version-owned economic aggregate. Rates and ownership are fractions, not percentages. */
public record EconomicConfiguration(int startYear, int years, String currency, BigDecimal wacc,
        List<TaxEntity> taxEntities, Set<String> boundary, List<NodeEconomicProfile> nodeProfiles,
        List<EconomicRule> adjustments, List<CommercialContract> contracts, List<CapitalAsset> assets,
        List<TaxTreatment> taxTreatments, List<MetricConversion> conversions) {

    public enum Direction { INCOME, EXPENSE }
    public enum TaxClassification { TAXABLE, DEDUCTIBLE, NON_TAXABLE, NON_DEDUCTIBLE, CAPITALIZABLE }
    public enum CashClassification { CASH, NON_CASH }
    public enum Driver { FIXED, INPUT_VOLUME, OUTPUT_VOLUME, EXPORTED_VOLUME, INSTALLED_CAPACITY,
        DISTANCE, CONNECTION_COUNT, OPERATING_HOURS, EVENT_COUNT, PERCENT_CAPEX }

    public record TaxEntity(String id, String name, String jurisdiction, BigDecimal taxRate,
            boolean carryLosses, BigDecimal openingLoss, Integer lossExpiryYears,
            BigDecimal lossOffsetLimit, int taxPaymentLagYears) {}
    public record Ownership(String taxEntityId, BigDecimal share) {}
    public record NodeEconomicProfile(UUID nodeId, List<Ownership> ownership, List<EconomicRule> rules) {}
    /** One occurrence per recognition date; no implicit recurrence or legacy cost import. */
    public record EconomicRule(String id, String concept, String taxEntityId, String counterpartyId,
            Direction direction, CashClassification cashClassification, Driver driver,
            BigDecimal unitValue, String unit, String currency, LocalDate validFrom, LocalDate validTo,
            List<Occurrence> occurrences) {}
    public record Occurrence(LocalDate recognitionDate, LocalDate cashDate) {}
    /** A null seller allocates revenue using the delivery node's ownership. */
    public record CommercialContract(String id, UUID deliveryNodeId, EconomicRule revenueRule) {}
    public record CapitalAsset(String id, UUID nodeId, String taxEntityId, String concept, String currency,
            BigDecimal cost, BigDecimal residualValue, LocalDate purchaseDate, LocalDate paymentDate,
            LocalDate serviceDate, int usefulLifeMonths) {}
    public record TaxTreatment(String concept, String taxEntityId, LocalDate validFrom, LocalDate validTo,
            TaxClassification classification, BigDecimal deductibleFraction) {}
    /** Static drivers use annualQuantity; observed drivers use simulator raw quantities times factor. */
    public record MetricConversion(UUID nodeId, Driver metric, String rawUnit, String unit,
            BigDecimal factor, BigDecimal annualQuantity) {}
}
