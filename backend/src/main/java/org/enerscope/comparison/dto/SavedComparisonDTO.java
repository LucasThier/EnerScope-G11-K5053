package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A saved comparison: its definition and its result, recalculated from the
 * immutable evaluations it points to.
 */
public record SavedComparisonDTO(UUID id,
                                 String name,
                                 String description,
                                 BigDecimal relevanceThreshold,
                                 BigDecimal rateThreshold,
                                 Instant createdAt,
                                 List<ComparisonRequestDTO.Item> items,
                                 ComparisonResultDTO result) {}
