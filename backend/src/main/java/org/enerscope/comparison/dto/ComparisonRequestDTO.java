package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What to compare. The first item is the baseline every other one is measured against.
 *
 * @param name                only required when saving the comparison
 * @param relevanceThreshold  relative change (fraction, 0.05 = 5%) from which a monetary or physical
 *                            difference is reported as relevant; {@code null} uses the default
 * @param rateThreshold       absolute change (fraction, 0.01 = 1 percentage point) from which a rate
 *                            difference (IRR, ROI, ROE) is reported as relevant; {@code null} uses the default
 * @param items               two or more evaluations, each identified by its version
 */
public record ComparisonRequestDTO(String name,
                                   String description,
                                   BigDecimal relevanceThreshold,
                                   BigDecimal rateThreshold,
                                   List<Item> items) {

    /**
     * One compared result.
     *
     * @param evaluationId a saved economic evaluation of the version; {@code null} picks the latest one
     */
    public record Item(UUID versionId, UUID evaluationId) {}
}
