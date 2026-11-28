package org.enerscope.scenario.dto;

import java.time.Instant;
import org.enerscope.comparison.model.enums.ScenarioStatus;
import java.util.UUID;

/**
 * A scenario of a version: its economic and simulation configuration.
 *
 * @param defaultScenario the one the old {@code /versions/{id}/...} endpoints act on
 * @param configured      a published economic configuration exists
 * @param draftPending    an unpublished draft exists; the scenario cannot be simulated until it is completed or discarded
 * @param status          where its latest simulation stands: {@code UP_TO_DATE}, {@code OUTDATED} (its configuration, or the
 *                        nodes of its version, changed since), {@code NEVER_SIMULATED} or {@code SIMULATING}
 * @param latestEvaluatedAt when its latest simulation was saved; null if it never was
 */
public record ScenarioDTO(UUID id, UUID versionId, String name, boolean defaultScenario, boolean configured,
                          boolean draftPending, ScenarioStatus status, Instant latestEvaluatedAt,
                          Instant createdAt, Instant lastModified) {}
