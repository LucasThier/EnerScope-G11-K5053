package org.enerscope.scenario.service;

import lombok.RequiredArgsConstructor;
import org.enerscope.logging.AppLogger;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the default scenario of a version, creating it when it does not exist yet.
 *
 * <p>While economics still hang from the version (E3 to E6), every write that creates or updates
 * a row of a version's economics also records the version's default scenario in {@code scenario_id}
 * (the "double write"); reads still use {@code version_id}. Callers hold the version's write lock
 * (see {@code EconomicService.lock}), and the unique index on the default scenario keeps a race from
 * creating a second one.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioDefaults {
    private final ScenarioRepository scenarios;
    private final AppLogger logger;

    /** The version's default scenario, or empty when it has none yet (a read never creates one). */
    @Transactional(readOnly = true)
    public java.util.Optional<Scenario> existing(java.util.UUID versionId) {
        return scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(versionId);
    }

    public Scenario forVersion(Version version) {
        return scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(version.getId()).orElseGet(() -> {
            Scenario created = scenarios.saveAndFlush(new Scenario(version, Scenario.DEFAULT_NAME, true));
            logger.info("Default scenario {} created for version {}", created.getId(), version.getId());
            return created;
        });
    }
}
