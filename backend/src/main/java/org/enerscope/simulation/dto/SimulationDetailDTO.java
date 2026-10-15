package org.enerscope.simulation.dto;

import java.util.List;

/** A simulation run with its breakdown by vertical and by node. */
public record SimulationDetailDTO(
        SimulationSummaryDTO summary,
        List<VerticalResultDTO> verticals,
        List<NodeResultDTO> nodes
) {}
