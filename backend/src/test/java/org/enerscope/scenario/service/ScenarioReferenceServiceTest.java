package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;

import static org.enerscope.economic.service.EconomicExample.MAPPER;

class ScenarioReferenceServiceTest {
    final EconomicConfigurationRepository configurations = mock(EconomicConfigurationRepository.class);
    final ScenarioReferenceService service = new ScenarioReferenceService(configurations, MAPPER);
    final Version version = new Version("V", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());

    @Test
    void aClonedNodeIsRemappedInTheConfigurationOfEveryScenarioOfTheVersion() {
        UUID oldId = UUID.randomUUID(), newId = UUID.randomUUID(), untouched = UUID.randomUUID();
        var first = new EconomicConfigurationEntity(version, "{\"conversions\":[{\"nodeId\":\"" + oldId + "\"}]}");
        var second = new EconomicConfigurationEntity(version, "{\"assets\":[{\"nodeId\":\"" + oldId + "\"},{\"nodeId\":\"" + untouched + "\"}]}");
        when(configurations.findAllByVersionId(version.getId())).thenReturn(List.of(first, second));

        service.remap(version, oldId, newId);

        assertTrue(first.getConfigurationJson().contains(newId.toString()));
        assertFalse(first.getConfigurationJson().contains(oldId.toString()));
        assertTrue(second.getConfigurationJson().contains(newId.toString()), "the second scenario must follow the clone too");
        assertFalse(second.getConfigurationJson().contains(oldId.toString()));
        assertTrue(second.getConfigurationJson().contains(untouched.toString()), "other nodes are left alone");
    }

    @Test
    void remappingANodeOntoItselfTouchesNothing() {
        UUID id = UUID.randomUUID();

        service.remap(version, id, id);

        verifyNoInteractions(configurations);
    }
}
