package org.enerscope.economic.model.configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.enerscope.economic.model.enums.*;

/** Version-owned economic aggregate. Rates and ownership are fractions, not percentages. */
public record EconomicConfiguration(int startYear,
                                    int years,
                                    String currency,
                                    BigDecimal wacc,
                                    List<TaxEntity> taxEntities,
                                    Set<String> boundary,
                                    List<NodeEconomicProfile> nodeProfiles,
                                    List<EconomicRule> adjustments,
                                    List<CommercialContract> contracts,
                                    List<CapitalAsset> assets,
                                    List<TaxTreatment> taxTreatments,
                                    List<MetricConversion> conversions) {}
