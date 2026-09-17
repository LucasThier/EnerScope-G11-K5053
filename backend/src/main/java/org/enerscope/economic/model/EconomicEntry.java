package org.enerscope.economic.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.enerscope.economic.model.EconomicConfiguration.*;

public record EconomicEntry(String id, UUID nodeId, String taxEntityId, String counterpartyId,
        String concept, String sourceRuleId, String contractId, Direction direction,
        TaxClassification taxClassification, CashClassification cashClassification,
        LocalDate recognitionDate, LocalDate cashDate, BigDecimal amount, String currency,
        BigDecimal deductibleFraction) {}
