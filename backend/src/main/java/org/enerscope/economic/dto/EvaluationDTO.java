package org.enerscope.economic.dto;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.enerscope.economic.model.*;
import org.enerscope.simulator.AnnualNodeMetrics;

public record EvaluationDTO(UUID id, Instant createdAt, Snapshot snapshot) {
    public record Snapshot(int schemaVersion, EconomicConfiguration configuration,
            JsonNode physicalNodes, JsonNode physicalConnections,
            List<AnnualNodeMetrics> rawMetrics, EconomicResult result) {}
}
