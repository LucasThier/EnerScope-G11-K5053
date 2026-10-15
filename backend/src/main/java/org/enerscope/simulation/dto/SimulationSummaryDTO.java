package org.enerscope.simulation.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A simulation run as it appears in a list.
 *
 * @param warnings codes the client turns into messages: {@code UNSIMULATED_NODES} (some nodes sit on a
 *                 cycle and could not be ordered, so they produced nothing) and {@code NO_SINKS} (the model
 *                 has no carrier or consumption node, so nothing can count as delivered)
 */
public record SimulationSummaryDTO(
        UUID id,
        UUID versionId,
        Instant createdAt,
        int years,
        int rounds,
        int nodeCount,
        SimulationKpisDTO kpis,
        List<String> warnings
) {}
