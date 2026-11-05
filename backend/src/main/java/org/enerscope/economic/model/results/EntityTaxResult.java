package org.enerscope.economic.model.results;

import java.math.BigDecimal;

public record EntityTaxResult(String taxEntityId,
                              int year,
                              BigDecimal resultBeforeTax,
                              BigDecimal lossesUsed,
                              BigDecimal lossesExpired,
                              BigDecimal closingLosses,
                              BigDecimal taxableBase,
                              BigDecimal tax,
                              int paymentYear) {}
