package org.enerscope.scenario.model;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
class ScenarioRevisionTest {
    private final UUID scenario = UUID.randomUUID();
    private ScenarioRevision revision(int number, String json, String hash) {
        return new ScenarioRevision(scenario,number,null,null,null,null,json,hash);
    }
    @Test void revisionRejectsInvalidNumber() { assertThrows(IllegalArgumentException.class,()->revision(0,"{}","a".repeat(64))); }
    @Test void revisionRequiresSnapshotAndHash() {
        assertThrows(IllegalArgumentException.class,()->revision(1," ","a".repeat(64)));
        assertThrows(IllegalArgumentException.class,()->revision(1,"{}","not-a-hash"));
    }
    @Test void importedRevisionAllowsUnknownAuthor() {
        var result=revision(1,"{}","a".repeat(64));
        assertNull(result.getCreatedBy()); assertEquals(scenario,result.getScenarioId());
    }
    @Test void revisionCannotBeActivatedOrDeactivated() {
        var result=revision(1,"{}","a".repeat(64));
        assertThrows(UnsupportedOperationException.class,result::activate);
        assertThrows(UnsupportedOperationException.class,result::deactivate);
    }
    @Test void mergeBaseRequiresMergeSource() {
        assertThrows(IllegalArgumentException.class,()->new ScenarioRevision(scenario,1,null,null,null,
                UUID.randomUUID(),"{}","a".repeat(64)));
    }
    @Test void publicationRejectsUnpersistedRevision() {
        var entity=new Scenario(scenario,UUID.randomUUID(),"Example",null,null);
        assertThrows(IllegalArgumentException.class,()->entity.publish(revision(1,"{}","a".repeat(64))));
    }
    @Test void scenarioDescriptionIsOptionalAndNameIsRequired() {
        var entity=new Scenario(scenario,UUID.randomUUID(),"Example",null,null);
        entity.updateDetails(" New name ",null); assertEquals("New name",entity.getName()); assertEquals("",entity.getDescription());
        assertThrows(IllegalArgumentException.class,()->entity.updateDetails(" ",""));
    }
}
