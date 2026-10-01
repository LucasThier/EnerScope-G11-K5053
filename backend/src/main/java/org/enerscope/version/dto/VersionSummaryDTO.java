package org.enerscope.version.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record VersionSummaryDTO(UUID id, String name, Instant lastModified, List<NodeSummary> nodes) {
    public record NodeSummary(UUID id, String name, String type) {}
}
