package org.enerscope.scenario.service;

import static org.enerscope.economic.service.EconomicExample.FIRST;
import static org.enerscope.economic.service.EconomicExample.MAPPER;
import static org.enerscope.economic.service.EconomicExample.SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.*;
import java.util.stream.Collectors;
import org.enerscope.common.ForbiddenException;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScenarioChangeScopeTest {

    private final ScenarioOperations operations = new ScenarioOperations(MAPPER);
    private final VersionAccessGuard guard = mock(VersionAccessGuard.class);
    private final ScenarioChangeScope scope = new ScenarioChangeScope(operations, guard, MAPPER, mock(AppLogger.class));
    private Version version;
    private EconomicConfiguration stored;

    @BeforeEach
    void setUp() {
        version = EconomicExample.version();
        node(FIRST).setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
        node(SECOND).setType(new NodeTypeData(VerticalEnum.EXPORT, StructuralRoleEnum.GENERATOR, NodeTypeEnum.SEAPORT_TERMINAL));
        version.getNodeSnapshot().forEach(node -> ((Well) node).setDeclineCurve(new ConstantValue(0)));
        stored = EconomicExample.configuration();
    }

    @Test
    void resendingEverythingUnchangedChangesNoNode() {
        assertEquals(Set.of(), ids(scope.changedNodes(version, unchangedOperations(), stored, EconomicExample.configuration())));
    }

    @Test
    void numericTextEqualToTheStoredValueIsNotAChange() {
        var supplied = unchangedOperations();
        supplied.get(SECOND).put("maxCollectionCapacity", TextNode.valueOf("500,0"));

        assertEquals(Set.of(), ids(scope.changedNodes(version, supplied, stored, stored)));
    }

    @Test
    void physicalFieldChangeMarksOnlyThatNode() {
        var supplied = unchangedOperations();
        supplied.get(SECOND).put("name", TextNode.valueOf("Renamed terminal"));

        assertEquals(Set.of(SECOND), ids(scope.changedNodes(version, supplied, stored, stored)));
    }

    @Test
    void invalidPhysicalValueCountsAsAChange() {
        var supplied = unchangedOperations();
        supplied.get(SECOND).put("maxCollectionCapacity", TextNode.valueOf("-1"));

        assertEquals(Set.of(SECOND), ids(scope.changedNodes(version, supplied, stored, stored)));
    }

    @Test
    void missingAndUnknownNodesAreIgnored() {
        var supplied = unchangedOperations();
        supplied.remove(SECOND);
        supplied.put(UUID.randomUUID(), Map.of("name", TextNode.valueOf("Ghost")));

        assertEquals(Set.of(), ids(scope.changedNodes(version, supplied, stored, stored)));
    }

    @Test
    void costRuleChangeOnANodeProfileMarksThatNode() {
        var requested = EconomicExample.mutate(tree -> profile(tree, SECOND).withArray("rules").forEach(rule ->
                ((ObjectNode) rule).put("unitValue", 99)));

        assertEquals(Set.of(SECOND), ids(scope.changedNodes(version, null, stored, requested)));
    }

    @Test
    void assetMovedBetweenNodesMarksBothNodes() {
        var requested = EconomicExample.mutate(tree ->
                ((ObjectNode) tree.withArray("assets").get(0)).put("nodeId", SECOND.toString()));

        assertEquals(Set.of(FIRST, SECOND), ids(scope.changedNodes(version, null, stored, requested)));
    }

    @Test
    void contractAndConversionAreAttributedToTheirNode() {
        var contract = EconomicExample.mutate(tree -> tree.withArray("contracts").forEach(row -> {
            if (row.path("deliveryNodeId").asText().equals(SECOND.toString())) {
                ((ObjectNode) row.path("revenueRule")).put("unitValue", 7);
            }
        }));
        var conversion = EconomicExample.mutate(tree -> tree.withArray("conversions").forEach(row -> {
            if (row.path("nodeId").asText().equals(SECOND.toString())) {
                ((ObjectNode) row).put("factor", 2);
            }
        }));

        assertEquals(Set.of(SECOND), ids(scope.changedNodes(version, null, stored, contract)));
        assertEquals(Set.of(SECOND), ids(scope.changedNodes(version, null, stored, conversion)));
    }

    @Test
    void globalOnlyChangeMarksNoNode() {
        var requested = EconomicExample.mutate(tree -> {
            tree.put("wacc", 0.25);
            ((ObjectNode) tree.withArray("taxEntities").get(0)).put("taxRate", 0.5);
            ((ObjectNode) tree.withArray("adjustments").get(0)).put("unitValue", 1);
        });

        assertEquals(Set.of(), ids(scope.changedNodes(version, unchangedOperations(), stored, requested)));
    }

    @Test
    void firstSaveWithoutStoredConfigurationMarksEveryReferencedNode() {
        assertEquals(Set.of(FIRST, SECOND), ids(scope.changedNodes(version, null, null, stored)));
    }

    @Test
    void reorderedRowsAndTrailingZerosAreNotAChange() {
        var requested = EconomicExample.mutate(tree -> {
            ArrayNode profiles = tree.withArray("nodeProfiles");
            JsonNode first = profiles.remove(0);
            profiles.add(first);
            tree.withArray("conversions").forEach(row -> ((ObjectNode) row).put("factor",
                    new java.math.BigDecimal(row.path("factor").asText() + "000")));
        });

        assertEquals(Set.of(), ids(scope.changedNodes(version, null, stored, requested)));
    }

    @Test
    void unparseableRequestedConfigurationSkipsTheEconomicComparison() {
        assertEquals(Set.of(), ids(scope.changedNodes(version, unchangedOperations(), stored, null)));
    }

    @Test
    void draftRowsAreComparedPerNodeAgainstThePreviousDraft() {
        ObjectNode previous = draft(row("r1", SECOND.toString(), "10"), row("r2", "", "5"));
        ObjectNode sameAgain = draft(row("r2", "", "5"), row("r1", SECOND.toString(), "10"));
        ObjectNode globalEdit = draft(row("r1", SECOND.toString(), "10"), row("r2", "", "6"));
        ObjectNode nodeEdit = draft(row("r1", SECOND.toString(), "11"), row("r2", "", "5"));

        assertEquals(Set.of(), ids(scope.changedDraftNodes(version, previous, sameAgain)));
        assertEquals(Set.of(), ids(scope.changedDraftNodes(version, previous, globalEdit)));
        assertEquals(Set.of(SECOND), ids(scope.changedDraftNodes(version, previous, nodeEdit)));
        assertEquals(Set.of(SECOND), ids(scope.changedDraftNodes(version, null, nodeEdit)));
        assertEquals(Set.of(), ids(scope.changedDraftNodes(version, null, draft(row("r1", "not-a-uuid", "1")))));
    }

    @Test
    void assertCanSaveChecksTheVerticalOfEachChangedNodeOnly() {
        var supplied = unchangedOperations();
        supplied.get(SECOND).put("name", TextNode.valueOf("Renamed terminal"));

        scope.assertCanSave(version, supplied, stored, stored);

        verify(guard).assertCanEditNodeVertical(version.getId(), VerticalEnum.EXPORT);
        verify(guard, never()).assertCanEditNodeVertical(version.getId(), VerticalEnum.EXTRACTION);
        verify(guard, never()).assertCanEditVersion(any());
    }

    @Test
    void assertCanSavePropagatesTheVerticalDenial() {
        var supplied = unchangedOperations();
        supplied.get(SECOND).put("name", TextNode.valueOf("Renamed terminal"));
        doThrow(new ForbiddenException("You are not allowed to edit this vertical's nodes in this version"))
                .when(guard).assertCanEditNodeVertical(version.getId(), VerticalEnum.EXPORT);

        assertThrows(ForbiddenException.class, () -> scope.assertCanSave(version, supplied, stored, stored));
    }

    @Test
    void untypedNodeWithChangedEconomicsFallsBackToVersionEdit() {
        node(SECOND).setType(null);
        var requested = EconomicExample.mutate(tree -> profile(tree, SECOND).withArray("rules").forEach(rule ->
                ((ObjectNode) rule).put("unitValue", 99)));

        scope.assertCanSave(version, null, stored, requested);

        verify(guard).assertCanEditVersion(version.getId());
        verify(guard, never()).assertCanEditNodeVertical(any(), any());
    }

    private BaseNode node(UUID id) {
        return version.getNodeSnapshot().stream().filter(n -> n.getId().equals(id)).findFirst().orElseThrow();
    }

    private Map<UUID, Map<String, JsonNode>> unchangedOperations() {
        var result = new HashMap<UUID, Map<String, JsonNode>>();
        for (var node : version.getNodeSnapshot()) {
            result.put(node.getId(), new HashMap<>(operations.describe(node).values()));
        }
        return result;
    }

    private static ObjectNode profile(ObjectNode tree, UUID nodeId) {
        for (JsonNode profile : tree.withArray("nodeProfiles")) {
            if (profile.path("nodeId").asText().equals(nodeId.toString())) {
                return (ObjectNode) profile;
            }
        }
        throw new AssertionError("No profile for " + nodeId);
    }

    private static ObjectNode draft(ObjectNode... rules) {
        ObjectNode draft = MAPPER.createObjectNode();
        draft.putArray("assets");
        ArrayNode rows = draft.putArray("rules");
        for (ObjectNode rule : rules) {
            rows.add(rule);
        }
        return draft;
    }

    private static ObjectNode row(String id, String nodeId, String value) {
        return MAPPER.createObjectNode().put("id", id).put("nodeId", nodeId).put("value", value);
    }

    private static Set<UUID> ids(Set<BaseNode> nodes) {
        assertTrue(nodes.stream().allMatch(Objects::nonNull));
        return nodes.stream().map(BaseNode::getId).collect(Collectors.toSet());
    }
}
