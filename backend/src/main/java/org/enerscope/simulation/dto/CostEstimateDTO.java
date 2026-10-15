package org.enerscope.simulation.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Cost of a version's current model over a horizon, computed on the spot without
 * simulating. Lets a scenario be priced while it is still being drawn.
 */
public record CostEstimateDTO(
        int years,
        BigDecimal capex,
        BigDecimal opex,
        BigDecimal upkeep,
        BigDecimal totalCost,
        List<VerticalResultDTO> verticals
) {}
