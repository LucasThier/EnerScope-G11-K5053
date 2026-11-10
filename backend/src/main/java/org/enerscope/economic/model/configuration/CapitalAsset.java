package org.enerscope.economic.model.configuration;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CapitalAsset(String id,
                           UUID nodeId,
                           String taxEntityId,
                           String concept,
                           String currency,
                           BigDecimal cost,
                           BigDecimal residualValue,
                           LocalDate purchaseDate,
                           LocalDate paymentDate,
                           LocalDate serviceDate,
                           int usefulLifeMonths) {}
