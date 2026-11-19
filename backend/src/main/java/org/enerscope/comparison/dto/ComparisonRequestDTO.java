package org.enerscope.comparison.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * What to compare. Scenarios are compared with each other; their order only decides their colour on screen.
 *
 * @param name                optional; a saved comparison without one is named after its scenarios
 * @param relevanceThreshold  relative change (fraction, 0.05 = 5%) from which a monetary or physical
 *                            difference is reported as relevant; {@code null} uses the default
 * @param rateThreshold       absolute change (fraction, 0.01 = 1 percentage point) from which a rate
 *                            difference (IRR, ROI, ROE) is reported as relevant; {@code null} uses the default
 * @param items               two to eight scenarios, from any project the caller can see; each one is
 *                            compared through its latest simulation, which must be up to date
 */
public record ComparisonRequestDTO(String name,
                                   String description,
                                   BigDecimal relevanceThreshold,
                                   BigDecimal rateThreshold,
                                   List<Item> items) {

    /** One compared scenario (version). */
    public record Item(UUID versionId) {}
}
