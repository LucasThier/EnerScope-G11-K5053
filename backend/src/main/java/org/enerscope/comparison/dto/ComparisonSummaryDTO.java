package org.enerscope.comparison.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A saved comparison as listed, without recalculating it. {@code scenarios} are version names, in the order they were picked. */
public record ComparisonSummaryDTO(UUID id,
                                   String name,
                                   String description,
                                   List<String> scenarios,
                                   Instant createdAt) {}
