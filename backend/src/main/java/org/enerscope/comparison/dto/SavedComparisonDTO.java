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
                                 List<Item> items,
                                 ComparisonResultDTO result) {

    /**
     * A compared scenario and the simulation the comparison was made with. The names are the ones the version and the
     * scenario had when it was saved; {@code deleted} says one of them has been deleted since (the saved result still opens).
     */
    public record Item(UUID scenarioId, UUID versionId, UUID evaluationId, String scenarioName, String versionName, boolean deleted) {}
}
