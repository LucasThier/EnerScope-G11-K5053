package org.enerscope.economic.dto;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.enerscope.economic.model.results.SimulationMetadata;
import org.enerscope.economic.model.results.ProbabilisticSummary;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.results.EconomicResult;
import org.enerscope.simulator.AnnualNodeMetrics;

public record EvaluationDTO(UUID id, Instant createdAt, Snapshot snapshot) {
    public record Snapshot(int schemaVersion,
                           EconomicConfiguration configuration,
                           JsonNode physicalNodes,
                           JsonNode physicalConnections,
                           List<AnnualNodeMetrics> rawMetrics,
                           EconomicResult result,
                           SimulationMetadata simulation,
                           ProbabilisticSummary summary) {

        public Snapshot(int schemaVersion, EconomicConfiguration configuration, JsonNode physicalNodes,
                JsonNode physicalConnections, List<AnnualNodeMetrics> rawMetrics, EconomicResult result) {
            this(schemaVersion, configuration, physicalNodes, physicalConnections, rawMetrics, result, null, null);
        }
    }
}
