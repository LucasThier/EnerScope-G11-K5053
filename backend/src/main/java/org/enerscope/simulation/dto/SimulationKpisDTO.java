package org.enerscope.simulation.dto;

import java.math.BigDecimal;

/**
 * Headline figures of a simulation run, the ones a comparison lines up side by
 * side. Volumes are totals over the run's horizon in the model's own units (the
 * capacities are entered per hour, so these are capacity units × hours).
 *
 * <p>Percentiles follow the reserves convention, so {@code deliveredP90 <=
 * deliveredP50 <= deliveredP10}: P90 is the conservative case and P10 the
 * optimistic one (see {@code SimulationResultMapper}).</p>
 *
 * @param deliveredMean mean, across rounds, of what the chain delivered (exports + domestic consumption)
 * @param deliveredP10  delivered volume in the optimistic round: only 10 % of the rounds reached it
 * @param deliveredP90  delivered volume in the conservative round: 90 % of the rounds reached it
 * @param exportedP50   LNG loaded onto carriers, in the P50 round
 * @param domesticP50   gas taken by domestic consumption nodes, in the P50 round
 * @param lossesP50     volume lost or consumed along the chain in the P50 round (transport losses,
 *                      treatment discharge, compression and liquefaction consumption)
 * @param unitCost      total cost divided by the P50 delivered volume; {@code null} when nothing was delivered
 */
public record SimulationKpisDTO(
        double deliveredMean,
        double deliveredP10,
        double deliveredP50,
        double deliveredP90,
        double exportedP50,
        double domesticP50,
        double lossesP50,
        BigDecimal capex,
        BigDecimal opex,
        BigDecimal upkeep,
        BigDecimal totalCost,
        BigDecimal unitCost
) {}
