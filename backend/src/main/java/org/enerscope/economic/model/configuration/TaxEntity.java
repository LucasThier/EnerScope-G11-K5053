package org.enerscope.economic.model.configuration;

import java.math.BigDecimal;

public record TaxEntity(String id,
                        String name,
                        String jurisdiction,
                        BigDecimal taxRate,
                        boolean carryLosses,
                        BigDecimal openingLoss,
                        Integer lossExpiryYears,
                        BigDecimal lossOffsetLimit,
                        int taxPaymentLagYears) {}
