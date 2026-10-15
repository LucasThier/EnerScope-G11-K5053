package org.enerscope.simulation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One node's figures in a simulation run. Production fields are {@code null} for
 * a node the engine could not place in its execution order.
 *
 * @param producedP50   volume the node put through in the P50 round
 * @param capacityP50   what it could have put through in that round (its capacity over the hours it was up)
 * @param utilizationP50 {@code producedP50 / capacityP50}, or {@code null} when the capacity is zero
 * @param lossesP50     the node's losses or process consumption in the P50 round
 */
public record NodeResultDTO(
        UUID nodeId,
        UUID identityId,
        String name,
        String nodeType,
        String vertical,
        String state,
        Double producedP10,
        Double producedP50,
        Double producedP90,
        Double capacityP50,
        Double utilizationP50,
        Double lossesP50,
        BigDecimal capex,
        BigDecimal opex,
        BigDecimal upkeep,
        BigDecimal totalCost
) {}
