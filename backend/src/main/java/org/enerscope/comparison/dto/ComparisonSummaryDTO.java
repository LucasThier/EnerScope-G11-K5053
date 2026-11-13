package org.enerscope.comparison.dto;

import java.time.Instant;
import java.util.UUID;

/** A saved comparison as listed, without recalculating it. */
public record ComparisonSummaryDTO(UUID id,
                                   String name,
                                   String description,
                                   int scenarioCount,
                                   Instant createdAt) {}
