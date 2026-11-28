package org.enerscope.scenario.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Maximum number of scenarios a version may have. Enforced by the service that creates scenarios
 * (not by the database), under the version's write lock ({@code ScenarioService.ensureRoom}). Configurable with
 * {@code enerscope.scenarios.max-per-version} ({@code SCENARIOS_MAX_PER_VERSION}); it must be at least 1.
 */
@Component
public class ScenarioLimits {
    public static final int DEFAULT_MAX_PER_VERSION = 20;
    private final int maxPerVersion;

    public ScenarioLimits(@Value("${enerscope.scenarios.max-per-version:" + DEFAULT_MAX_PER_VERSION + "}") int maxPerVersion) {
        if (maxPerVersion < 1) throw new IllegalArgumentException("enerscope.scenarios.max-per-version must be at least 1");
        this.maxPerVersion = maxPerVersion;
    }

    public int maxPerVersion() {
        return maxPerVersion;
    }
}
