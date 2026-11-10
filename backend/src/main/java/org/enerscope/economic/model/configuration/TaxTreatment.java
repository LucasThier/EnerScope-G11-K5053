package org.enerscope.economic.model.configuration;

import org.enerscope.economic.model.enums.TaxClassification;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxTreatment(String concept,
                           String taxEntityId,
                           LocalDate validFrom,
                           LocalDate validTo,
                           TaxClassification classification,
                           BigDecimal deductibleFraction) {}
