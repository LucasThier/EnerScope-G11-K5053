package org.enerscope.economic.model;

import java.math.BigDecimal;
import java.util.UUID;
import org.enerscope.economic.model.EconomicConfiguration.Driver;

public record OperationalMetric(UUID nodeId, int year, Driver metric, String unit, BigDecimal quantity) {}
