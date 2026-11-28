package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.Optional;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.logging.AppLogger;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.junit.jupiter.api.Test;

class ScenarioDefaultsTest {
    final ScenarioRepository repository = EconomicExample.noScenariosYet();
    final ScenarioDefaults defaults = new ScenarioDefaults(repository, mock(AppLogger.class));
    final org.enerscope.version.model.Version version = EconomicExample.version();

    @Test
    void createsTheEscenarioBaseOfAVersionThatHasNone() {
        var created = defaults.forVersion(version);

        assertEquals("Escenario base", created.getName());
        assertTrue(created.isDefaultScenario());
        assertSame(version, created.getVersion());
        verify(repository).saveAndFlush(created);
    }

    @Test
    void lookingForTheDefaultScenarioOfAVersionWithoutOneNeverCreatesIt() {
        assertTrue(defaults.existing(version.getId()).isEmpty());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void lookingForTheDefaultScenarioReturnsTheExistingOne() {
        var existing = new Scenario(version, Scenario.DEFAULT_NAME, true);
        when(repository.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(version.getId())).thenReturn(Optional.of(existing));

        assertSame(existing, defaults.existing(version.getId()).orElseThrow());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void reusesTheExistingDefaultScenarioWithoutCreatingAnother() {
        var existing = new Scenario(version, Scenario.DEFAULT_NAME, true);
        when(repository.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(version.getId())).thenReturn(Optional.of(existing));

        assertSame(existing, defaults.forVersion(version));
        verify(repository, never()).saveAndFlush(any());
    }
}
