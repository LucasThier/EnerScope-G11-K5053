package org.enerscope.simulation.dto;

import java.math.BigDecimal;

/**
 * A run's figures for one vertical of the value chain (extraction, transport,
 * liquefaction, export, domestic consumption). Production is deliberately not
 * summed here: every node in a vertical handles the same gas in turn, so adding
 * up their throughput would count it several times.
 *
 * @param lossesP50 losses of the vertical's nodes in the P50 round; {@code null} in a cost estimate,
 *                  where nothing was simulated
 */
public record VerticalResultDTO(
        String vertical,
        int nodeCount,
        Double lossesP50,
        BigDecimal capex,
        BigDecimal opex,
        BigDecimal upkeep,
        BigDecimal totalCost
) {}
