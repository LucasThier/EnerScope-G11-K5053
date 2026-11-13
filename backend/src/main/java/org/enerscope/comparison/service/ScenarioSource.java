package org.enerscope.comparison.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.results.EconomicRoundResult;

/**
 * A loaded evaluation ready to be measured: the saved snapshot plus, for
 * probabilistic evaluations, every saved round (deterministic ones have none).
 */
public record ScenarioSource(UUID versionId,
                             String versionName,
                             UUID evaluationId,
                             Instant evaluatedAt,
                             EvaluationDTO.Snapshot snapshot,
                             List<EconomicRoundResult> rounds) {

    public ScenarioSource {
        rounds = rounds == null ? List.of() : List.copyOf(rounds);
    }
}
