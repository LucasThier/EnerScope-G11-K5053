package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ScenarioLimitsTest {
    @Test
    void theDefaultLimitIsTwentyScenariosPerVersion() {
        assertEquals(20, ScenarioLimits.DEFAULT_MAX_PER_VERSION);
        assertEquals(20, new ScenarioLimits(ScenarioLimits.DEFAULT_MAX_PER_VERSION).maxPerVersion());
    }

    @Test
    void aLimitBelowOneIsRejectedAtStartup() {
        assertThrows(IllegalArgumentException.class, () -> new ScenarioLimits(0));
    }
}
