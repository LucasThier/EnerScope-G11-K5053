package org.enerscope.simulation.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One version in a comparison.
 *
 * @param nodesByVertical  active (non-removed) nodes per vertical
 * @param diff             changes against the baseline; {@code null} for the baseline itself
 * @param latestSimulation the version's most recent run, or {@code null} if it was never simulated
 * @param verticals        that run's figures per vertical; {@code null} without a run
 */
public record ComparedVersionDTO(
        UUID versionId,
        String name,
        UUID parentVersionId,
        Instant createdAt,
        int nodeCount,
        int connectionCount,
        Map<String, Integer> nodesByVertical,
        StructuralDiffDTO diff,
        SimulationSummaryDTO latestSimulation,
        List<VerticalResultDTO> verticals
) {}
