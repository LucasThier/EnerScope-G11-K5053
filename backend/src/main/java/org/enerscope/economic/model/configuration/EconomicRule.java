package org.enerscope.economic.model.configuration;

import org.enerscope.economic.model.enums.CashClassification;
import org.enerscope.economic.model.enums.Direction;
import org.enerscope.economic.model.enums.Driver;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One occurrence per recognition date; no implicit recurrence or legacy cost import. */
public record EconomicRule(String id,
                           String concept,
                           String taxEntityId,
                           String counterpartyId,
                           Direction direction,
                           CashClassification cashClassification,
                           Driver driver,
                           BigDecimal unitValue,
                           String unit,
                           String currency,
                           LocalDate validFrom,
                           LocalDate validTo,
                           List<Occurrence> occurrences) {}
