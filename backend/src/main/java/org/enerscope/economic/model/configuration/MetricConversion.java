package org.enerscope.economic.model.configuration;

import org.enerscope.economic.model.enums.Driver;

import java.math.BigDecimal;
import java.util.UUID;

/** Static drivers use annualQuantity; observed drivers use simulator raw quantities times factor. */
public record MetricConversion(UUID nodeId,
                               Driver metric,
                               String rawUnit,
                               String unit,
                               BigDecimal factor,
                               BigDecimal annualQuantity) {
}
