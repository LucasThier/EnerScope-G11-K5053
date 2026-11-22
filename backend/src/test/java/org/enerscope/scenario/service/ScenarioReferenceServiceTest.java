package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.scenario.model.EconomicDraftEntity;
import org.enerscope.scenario.model.ScenarioDraft;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;

import static org.enerscope.economic.service.EconomicExample.MAPPER;

class ScenarioReferenceServiceTest {
    final EconomicConfigurationRepository configurations = mock(EconomicConfigurationRepository.class);
    final ScenarioDraftRepository scenarioDrafts = mock(ScenarioDraftRepository.class);
    final EconomicDraftRepository economicDrafts = mock(EconomicDraftRepository.class);
    final ScenarioReferenceService service = new ScenarioReferenceService(configurations, scenarioDrafts, economicDrafts, MAPPER);
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

        verifyNoInteractions(configurations, scenarioDrafts, economicDrafts);
    }

    @Test
    void aClonedNodeIsRemappedInTheScenarioDraftAndTheLegacyDraftOfEveryScenario() {
        UUID oldId = UUID.randomUUID(), newId = UUID.randomUUID(), untouched = UUID.randomUUID();
        var firstDraft = new ScenarioDraft(version, "{\"configuration\":{\"conversions\":[{\"nodeId\":\"" + oldId + "\"}]}}");
        var secondDraft = new ScenarioDraft(version, "{\"editor\":{\"value\":{\"assets\":[{\"nodeId\":\"" + oldId + "\"},{\"nodeId\":\"" + untouched + "\"}]}}}");
        var legacy = new EconomicDraftEntity(version, "{\"draft\":{\"rules\":[{\"nodeId\":\"" + oldId + "\"}]}}");
        when(scenarioDrafts.findAllByVersionId(version.getId())).thenReturn(List.of(firstDraft, secondDraft));
        when(economicDrafts.findAllByVersionId(version.getId())).thenReturn(List.of(legacy));

        service.remap(version, oldId, newId);

        for (String json : List.of(firstDraft.getDraftJson(), secondDraft.getDraftJson(), legacy.getDraftJson())) {
            assertTrue(json.contains(newId.toString()));
            assertFalse(json.contains(oldId.toString()));
        }
        assertTrue(secondDraft.getDraftJson().contains(untouched.toString()), "other nodes are left alone");
    }

    @Test
    void draftsWithoutTheNodeOrWithBlankJsonAreLeftExactlyAsTheyAre() {
        UUID oldId = UUID.randomUUID(), newId = UUID.randomUUID(), other = UUID.randomUUID();
        String unrelatedJson = "{\"configuration\":{\"conversions\":[{\"nodeId\":\"" + other + "\",\"factor\":1.50}]}}";
        var unrelated = new ScenarioDraft(version, unrelatedJson);
        var blank = new ScenarioDraft(version, "");
        var empty = new EconomicDraftEntity(version, "{}");
        var noReferences = new EconomicDraftEntity(version, "{\"note\":\"" + oldId + "\"}");
        when(scenarioDrafts.findAllByVersionId(version.getId())).thenReturn(List.of(unrelated, blank));
        when(economicDrafts.findAllByVersionId(version.getId())).thenReturn(List.of(empty, noReferences));

        service.remap(version, oldId, newId);

        assertEquals(unrelatedJson, unrelated.getDraftJson(), "not rewritten, not even re-serialized");
        assertEquals("", blank.getDraftJson());
        assertEquals("{}", empty.getDraftJson());
        assertEquals("{\"note\":\"" + oldId + "\"}", noReferences.getDraftJson(), "only nodeId / deliveryNodeId keys are references");
    }
}
