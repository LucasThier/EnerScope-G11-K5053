package org.enerscope.scenario.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class OperationalThreeWayMergeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UUID id = UUID.randomUUID();
    private JsonNode node(int capacity) { return mapper.createObjectNode().put("capacity", capacity); }
    @Test void independentChangesAreCombined() {
        UUID other = UUID.randomUUID();
        var result = OperationalThreeWayMerge.merge(Map.of(id,node(1)), Map.of(id,node(2)),
                Map.of(id,node(1),other,node(3)),Map.of());
        assertEquals(Map.of(id,node(2),other,node(3)),result.values()); assertTrue(result.conflicts().isEmpty());
    }
    @Test void deletionAgainstModificationRequiresResolution() {
        var result=OperationalThreeWayMerge.merge(Map.of(id,node(1)),Map.of(),Map.of(id,node(2)),Map.of());
        assertEquals(1,result.conflicts().size()); assertNull(result.conflicts().getFirst().source());
        assertEquals(node(2),result.values().get(id));
    }
    @Test void choosingSourceCanResolveDeletion() {
        var result=OperationalThreeWayMerge.merge(Map.of(id,node(1)),Map.of(),Map.of(id,node(2)),
                Map.of(id,OperationalThreeWayMerge.Choice.SOURCE));
        assertTrue(result.values().isEmpty()); assertTrue(result.conflicts().isEmpty());
    }
    @Test void choosingTargetKeepsItsValue() {
        var result=OperationalThreeWayMerge.merge(Map.of(id,node(1)),Map.of(id,node(3)),Map.of(id,node(2)),
                Map.of(id,OperationalThreeWayMerge.Choice.TARGET));
        assertEquals(node(2),result.values().get(id)); assertTrue(result.conflicts().isEmpty());
    }
    @Test void unknownBaseRequiresReviewEvenForSourceOnlyAddition() {
        var result=OperationalThreeWayMerge.merge(null,Map.of(id,node(3)),Map.of(),Map.of());
        assertEquals(1,result.conflicts().size()); assertTrue(result.values().isEmpty());
    }
    @Test void identicalChangesDoNotConflict() {
        var result=OperationalThreeWayMerge.merge(Map.of(id,node(1)),Map.of(id,node(3)),Map.of(id,node(3)),Map.of());
        assertTrue(result.conflicts().isEmpty()); assertEquals(node(3),result.values().get(id));
    }
    @Test void unchangedSourcePreservesTargetDeletion() {
        var result=OperationalThreeWayMerge.merge(Map.of(id,node(1)),Map.of(id,node(1)),Map.of(),Map.of());
        assertTrue(result.conflicts().isEmpty()); assertTrue(result.values().isEmpty());
    }
    @Test void inputTreesAreNotSharedWithResult() {
        var original=node(1);
        var result=OperationalThreeWayMerge.merge(Map.of(),Map.of(id,original),Map.of(),Map.of());
        ((com.fasterxml.jackson.databind.node.ObjectNode) result.values().get(id)).put("capacity",9);
        assertEquals(node(1),original);
    }
    @Test void staleResolutionIsRejected() {
        assertThrows(IllegalArgumentException.class,()->OperationalThreeWayMerge.merge(Map.of(),Map.of(),Map.of(),
                Map.of(id,OperationalThreeWayMerge.Choice.SOURCE)));
    }
    @Test void resolutionForNonConflictIsRejected() {
        assertThrows(IllegalArgumentException.class,()->OperationalThreeWayMerge.merge(Map.of(),Map.of(id,node(1)),Map.of(),
                Map.of(id,OperationalThreeWayMerge.Choice.SOURCE)));
    }
}
