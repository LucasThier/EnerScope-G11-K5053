package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.dto.InvestmentCostDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.dto.NodeTypeDataDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.dto.VersionConflictDTO;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end reproduction, against a real (H2) Hibernate session, of the
 * scenario reported by the user: two children of the same parent each touch
 * a node, one gets merged, and the other should show up via
 * {@code GET /version/{siblingId}/conflicts}.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VersionConflictIntegrationTest {

    @Autowired
    private VersionService versionService;

    @Autowired
    private VersionConflictService versionConflictService;

    @Autowired
    private VersionRepository versionRepository;

    private WellDTO wellDTO(String name, UUID identity) {
        WellDTO dto = new WellDTO();
        dto.setName(name);
        dto.setState(NodeStateEnum.PROPOSED);
        dto.setLifespanInMonths(120);
        dto.setUpkeepCosts(100f);
        dto.setMaintenanceIntervalInDays(30);
        dto.setOperatingCosts(50f);
        dto.setWastePercentage(0.1f);
        InvestmentCostDTO investmentCost = new InvestmentCostDTO();
        investmentCost.setComponents(new ArrayList<>());
        dto.setInvestmentCost(investmentCost);
        dto.setGraphData(new NodeGraphDataDTO(1.0, 2.0, 3.0));
        dto.setType(new NodeTypeDataDTO(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
        dto.setIdentity(identity);
        dto.setMaxCollectionCapacity(10f);
        dto.setDeclineCurve(0.5f);
        dto.setGasRichness(0.5f);
        dto.setDTMTime(5);
        dto.setDTMCost(50f);
        dto.setSurface(100f);
        return dto;
    }

    @Test
    void mergingOneChildFlagsAConflictOnTheSiblingThatTouchedTheSameIdentity() {
        Version parent = versionService.saveVersion(new VersionDTO("Parent", null));
        Version source = versionService.saveVersion(new VersionDTO("Source", parent.getId()));
        Version sibling = versionService.saveVersion(new VersionDTO("Sibling", parent.getId()));

        UUID sharedIdentity = UUID.randomUUID();
        versionService.addNodeToVersion(source.getId(), wellDTO("Source Well", sharedIdentity));
        versionService.addNodeToVersion(sibling.getId(), wellDTO("Sibling Well", sharedIdentity));

        versionService.mergeSubVersionIntoParent(source.getId());

        List<VersionConflictDTO> conflicts = versionConflictService.getConflictsForVersion(sibling.getId());

        assertEquals(1, conflicts.size(), "sibling touched the same identity as the merged source, expected one conflict");
        assertEquals(sharedIdentity, conflicts.get(0).getIdentityId());
        assertEquals(sibling.getId(), conflicts.get(0).getConflictingVersionId());
    }

    @Test
    void mergingANewlyAddedNodeFlagsAConflictWithSiblingsThatDontHaveIt() {
        // The sibling never touched this node - but its own snapshot is a static
        // copy taken when it branched off, so it simply doesn't have the node the
        // source just added. Flagged so a human decides whether the sibling
        // should adopt it too, rather than the node quietly never reaching it.
        Version parent = versionService.saveVersion(new VersionDTO("Parent2", null));
        Version source = versionService.saveVersion(new VersionDTO("Source2", parent.getId()));
        Version sibling = versionService.saveVersion(new VersionDTO("Sibling2", parent.getId()));

        versionService.addNodeToVersion(source.getId(), wellDTO("Only In Source", UUID.randomUUID()));

        versionService.mergeSubVersionIntoParent(source.getId());

        List<VersionConflictDTO> conflicts = versionConflictService.getConflictsForVersion(sibling.getId());

        assertEquals(1, conflicts.size(),
                "sibling has no row at all for the identity the merge just added, expected a conflict");
    }

    @Test
    void mergingASiblingThatNeverTouchedAnythingDoesNotRevertWhatAnEarlierMergeAdded() {
        // The merge is differential now (VersionService#applyNodeChangesToSnapshot):
        // it only applies what the merging branch itself changed, so a sibling
        // that changed nothing of its own can't wipe out another branch's merge.
        Version parent = versionService.saveVersion(new VersionDTO("Parent6", null));
        Version branchA = versionService.saveVersion(new VersionDTO("BranchA", parent.getId()));
        Version branchB = versionService.saveVersion(new VersionDTO("BranchB", parent.getId()));

        versionService.addNodeToVersion(branchA.getId(), wellDTO("Added By A", UUID.randomUUID()));
        versionService.mergeSubVersionIntoParent(branchA.getId());

        versionService.mergeSubVersionIntoParent(branchB.getId());

        Version reloadedParent = versionRepository.findById(parent.getId()).orElseThrow();
        assertEquals(1, reloadedParent.getNodeSnapshot().size(), "branchA's node must survive branchB's merge");
        assertEquals("Added By A", reloadedParent.getNodeSnapshot().get(0).getName());
    }

    @Test
    void repeatedMergesTouchingTheSameIdentityCollapseToOneNetNodeChange() {
        // Reproduces the reported bug: merging into the same identity across
        // several separate merges used to accumulate stale history entries
        // (e.g. ADD, EDIT, ADD) instead of collapsing to the net change,
        // because change lists were grouped by raw row id - which changes on
        // every clone-on-write edit - instead of by identity.
        Version parent = versionService.saveVersion(new VersionDTO("Parent7", null));
        UUID identity = UUID.randomUUID();

        Version branch1 = versionService.saveVersion(new VersionDTO("Branch7a", parent.getId()));
        versionService.addNodeToVersion(branch1.getId(), wellDTO("First", identity));
        versionService.mergeSubVersionIntoParent(branch1.getId());

        Version afterFirstMerge = versionRepository.findById(parent.getId()).orElseThrow();
        BaseNode currentNode = afterFirstMerge.getNodeSnapshot().get(0);

        Version branch2 = versionService.saveVersion(new VersionDTO("Branch7b", parent.getId()));
        versionService.editNodeInVersion(branch2.getId(), currentNode.getId(), wellDTO("Second", identity));
        versionService.mergeSubVersionIntoParent(branch2.getId());

        // A third branch independently re-adds the same identity, exactly like
        // the reported reproduction (a client reusing an explicit identity).
        Version branch3 = versionService.saveVersion(new VersionDTO("Branch7c", parent.getId()));
        versionService.addNodeToVersion(branch3.getId(), wellDTO("Third", identity));
        versionService.mergeSubVersionIntoParent(branch3.getId());

        Version finalParent = versionRepository.findById(parent.getId()).orElseThrow();
        assertEquals(1, finalParent.getNodeSnapshot().size());
        assertEquals("Third", finalParent.getNodeSnapshot().get(0).getName());
        assertEquals(1, finalParent.getNodeChanges().size(),
                "history for one identity across three merges must collapse to a single net entry");
    }

    @Test
    void getAllConflicts_ReturnsConflictsAcrossAllVersions() {
        Version parent = versionService.saveVersion(new VersionDTO("Parent3", null));
        Version source = versionService.saveVersion(new VersionDTO("Source3", parent.getId()));
        Version sibling = versionService.saveVersion(new VersionDTO("Sibling3", parent.getId()));

        UUID sharedIdentity = UUID.randomUUID();
        versionService.addNodeToVersion(source.getId(), wellDTO("Source Well", sharedIdentity));
        versionService.addNodeToVersion(sibling.getId(), wellDTO("Sibling Well", sharedIdentity));

        versionService.mergeSubVersionIntoParent(source.getId());

        List<VersionConflictDTO> all = versionConflictService.getAllConflicts();

        assertTrue(all.stream().anyMatch(c -> c.getConflictingVersionId().equals(sibling.getId())));
    }

    @Test
    void editingAnInheritedNodeInOneChildDoesNotMutateTheParentOrSiblingRows() {
        // Reproduces the reported bug: a node inherited from the parent must be
        // cloned-on-write, never mutated in place, or every version sharing that
        // row (the parent, and any sibling that hasn't touched it) sees the edit.
        Version parent = versionService.saveVersion(new VersionDTO("Parent4", null));
        UUID identity = UUID.randomUUID();
        BaseNode addedNode = versionService.addNodeToVersion(parent.getId(), wellDTO("Shared Well", identity));

        Version childA = versionService.saveVersion(new VersionDTO("ChildA", parent.getId()));
        Version childB = versionService.saveVersion(new VersionDTO("ChildB", parent.getId()));

        WellDTO edit = wellDTO("Edited In ChildA", identity);
        BaseNode editedInA = versionService.editNodeInVersion(childA.getId(), addedNode.getId(), edit);

        assertNotEquals(addedNode.getId(), editedInA.getId(), "editing an inherited node must clone it, not reuse its id");
        assertEquals(identity, editedInA.getIdentityId());

        Version reloadedParent = versionRepository.findById(parent.getId()).orElseThrow();
        Version reloadedChildB = versionRepository.findById(childB.getId()).orElseThrow();

        assertEquals("Shared Well", nameOfIdentity(reloadedParent, identity), "parent's own node must be untouched");
        assertEquals("Shared Well", nameOfIdentity(reloadedChildB, identity), "sibling's own node must be untouched");
        assertEquals("Edited In ChildA", nameOfIdentity(versionRepository.findById(childA.getId()).orElseThrow(), identity));
    }

    @Test
    void editingAnInheritedConnectionInOneChildDoesNotMutateTheParentOrSiblingRows() {
        Version parent = versionService.saveVersion(new VersionDTO("Parent5", null));
        BaseNode nodeA = versionService.addNodeToVersion(parent.getId(), wellDTO("Node A", UUID.randomUUID()));
        BaseNode nodeB = versionService.addNodeToVersion(parent.getId(), wellDTO("Node B", UUID.randomUUID()));
        BaseNode nodeC = versionService.addNodeToVersion(parent.getId(), wellDTO("Node C", UUID.randomUUID()));

        UUID identity = UUID.randomUUID();
        ConnectionDTO connectionDTO = new ConnectionDTO();
        connectionDTO.setFromNodeId(nodeA.getId());
        connectionDTO.setToNodeId(nodeB.getId());
        connectionDTO.setIdentity(identity);
        NodeConnection addedConnection = versionService.addConnectionToVersion(parent.getId(), connectionDTO);

        Version childA = versionService.saveVersion(new VersionDTO("ConnChildA", parent.getId()));
        Version childB = versionService.saveVersion(new VersionDTO("ConnChildB", parent.getId()));

        ConnectionDTO editDTO = new ConnectionDTO();
        editDTO.setFromNodeId(nodeA.getId());
        editDTO.setToNodeId(nodeC.getId());
        NodeConnection editedInA = versionService.editConnectionInVersion(childA.getId(), addedConnection.getId(), editDTO);

        assertNotEquals(addedConnection.getId(), editedInA.getId());
        assertEquals(identity, editedInA.getIdentityId());
        assertEquals(nodeC.getId(), editedInA.getToNodeId());

        Version reloadedParent = versionRepository.findById(parent.getId()).orElseThrow();
        Version reloadedChildB = versionRepository.findById(childB.getId()).orElseThrow();

        assertEquals(nodeB.getId(), toNodeOfIdentity(reloadedParent, identity), "parent's own connection must be untouched");
        assertEquals(nodeB.getId(), toNodeOfIdentity(reloadedChildB, identity), "sibling's own connection must be untouched");
    }

    private String nameOfIdentity(Version version, UUID identity) {
        return version.getNodeSnapshot().stream()
                .filter(n -> identity.equals(n.getIdentityId()))
                .findFirst()
                .orElseThrow()
                .getName();
    }

    private UUID toNodeOfIdentity(Version version, UUID identity) {
        return version.getConnectionSnapshot().stream()
                .filter(c -> identity.equals(c.getIdentityId()))
                .findFirst()
                .orElseThrow()
                .getToNodeId();
    }
}
