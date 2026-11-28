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
 * @param items               two to eight scenarios of the path project, from the same or from different versions; each
 *                            one is compared through its latest simulation, which must be up to date
 */
public record ComparisonRequestDTO(String name,
                                   String description,
                                   BigDecimal relevanceThreshold,
                                   BigDecimal rateThreshold,
                                   List<Item> items) {

    /**
     * One compared scenario.
     *
     * @param scenarioId the scenario to compare
     * @param versionId  <b>deprecated</b>, accepted only so requests written before scenarios existed keep working: it is
     *                   resolved to the default scenario of that version. Send {@code scenarioId}.
     */
    public record Item(UUID scenarioId, @Deprecated UUID versionId) {
        public Item(UUID scenarioId) {
            this(scenarioId, null);
        }

        /** A compatibility item naming only a version (its default scenario is compared). */
        public static Item ofVersion(UUID versionId) {
            return new Item(null, versionId);
        }
    }
}
