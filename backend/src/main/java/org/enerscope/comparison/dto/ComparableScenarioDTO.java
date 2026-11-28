package org.enerscope.comparison.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.comparison.model.enums.ScenarioStatus;

/**
 * A scenario as the comparison picker shows it, grouped by its version: whether its latest simulation can be compared,
 * and whether the caller can (re)simulate it from there.
 *
 * @param scenarioId         the scenario; what a comparison request names
 * @param defaultScenario    the scenario the old version-level routes act on
 * @param status             where this scenario's latest simulation stands (per scenario, not per version)
 * @param latestEvaluationId the simulation a comparison would use; {@code null} if never simulated
 * @param ready              the scenario can be simulated now (complete configuration, no pending draft)
 * @param readinessErrors    why it cannot be simulated, as the scenario configuration reports it
 * @param canSimulate        the caller may run simulations of the scenario's version
 * @param job                the running simulation, or the last one when it failed after the latest result
 */
public record ComparableScenarioDTO(UUID projectId,
                                    String projectName,
                                    UUID versionId,
                                    String versionName,
                                    UUID scenarioId,
                                    String scenarioName,
                                    boolean defaultScenario,
                                    ScenarioStatus status,
                                    UUID latestEvaluationId,
                                    Instant latestEvaluatedAt,
                                    EvaluationMode mode,
                                    Integer rounds,
                                    boolean ready,
                                    List<String> readinessErrors,
                                    boolean canSimulate,
                                    SimulationJob job) {

    public record SimulationJob(UUID id, String status, int completedRounds, int rounds, String errorMessage) {}
}
