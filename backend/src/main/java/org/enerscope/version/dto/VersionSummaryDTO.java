package org.enerscope.version.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Shared lightweight summary for the diagram and economic scenario selectors. */
public record VersionSummaryDTO(UUID id, String name, Instant lastModified, List<NodeSummary> nodes,
                                UUID parentVersionId, Instant createdAt) {
    public VersionSummaryDTO(UUID id, String name, Instant lastModified, List<NodeSummary> nodes) {
        this(id, name, lastModified, nodes, null, null);
    }
    public record NodeSummary(UUID id, String name, String type) {}
}
