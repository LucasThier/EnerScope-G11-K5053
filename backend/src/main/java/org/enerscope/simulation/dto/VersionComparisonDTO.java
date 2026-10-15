package org.enerscope.simulation.dto;

import java.util.List;
import java.util.UUID;

/**
 * Two or more versions of a project side by side. The first version requested
 * is the baseline every other one is diffed against.
 *
 * @param warnings codes the client turns into messages: {@code MISSING_SIMULATIONS} (some version was
 *                 never simulated, so its figures are empty) and {@code DIFFERENT_HORIZONS} (the latest
 *                 runs cover different numbers of years, so their totals are not like for like)
 */
public record VersionComparisonDTO(
        UUID projectId,
        UUID baselineVersionId,
        List<ComparedVersionDTO> versions,
        List<String> warnings
) {}
