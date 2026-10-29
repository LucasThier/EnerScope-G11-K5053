package org.enerscope.simulator;

import java.math.BigDecimal;
import java.util.UUID;

/** Raw simulator units; conversions are explicit and version-scoped. */
public record AnnualNodeMetrics(UUID nodeId, int period, BigDecimal input, BigDecimal output,
        BigDecimal exported, BigDecimal losses, BigDecimal operatingHours, BigDecimal events) {}
