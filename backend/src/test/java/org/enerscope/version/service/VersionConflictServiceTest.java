package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.repository.BaseNodeRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.version.dto.ConflictResolutionType;
import org.enerscope.version.dto.VersionConflictDTO;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
import org.enerscope.version.repository.VersionConflictRepository;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VersionConflictServiceTest {

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private VersionConflictRepository conflictRepository;

    @Mock
    private BaseNodeRepository nodeRepository;

    @Mock
    private NodeConnectionRepository connectionRepository;

    @Mock
    private AppLogger logger;

    private VersionConflictService service;

    @BeforeEach
    void setUp() {
        service = new VersionConflictService(versionRepository, conflictRepository, nodeRepository,
                connectionRepository, logger);
    }

    private Version version(String name) {
        Version v = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        ReflectionTestUtils.setField(v, "id", UUID.randomUUID());
        return v;
    }

    private Well well(String name, UUID identityId) {
        Well well = new Well(name, NodeStateEnum.PROPOSED, null, 120,
                MoneyAmount.of(1000), 30, MoneyAmount.of(500), 0.1f,
                new InvestmentCost(new ArrayList<>(List.of(
                        new InvestmentCostComponent("drilling", MoneyAmount.of(100), CostBasisEnum.PER_M)))),
                new NodeGraphData(1.0, 2.0, 3.0),
                identityId,
                new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                10f, 0.5f, 0.5f, 5, MoneyAmount.of(50), 100f);
        ReflectionTestUtils.setField(well, "id", UUID.randomUUID());
        return well;
    }

    private NodeConnection connection(UUID identityId, UUID fromId, UUID toId) {
        NodeConnection connection = new NodeConnection();
        connection.setIdentityId(identityId);
        connection.setFromNodeId(fromId);
        connection.setToNodeId(toId);
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        return connection;
    }

    private void editNode(Version v, org.enerscope.node.model.BaseNode node) {
        NodeChange change = new NodeChange();
        change.setChangeType(ChangeTypeEnum.EDIT);
        change.setChangedNode(node);
        change.setResultNode(node);
        v.getNodeChanges().add(change);
    }

    private void editConnection(Version v, NodeConnection connection) {
        ConnectionChange change = new ConnectionChange();
        change.setChangeType(ChangeTypeEnum.EDIT);
        change.setChangedConnection(connection);
        change.setResultConnection(connection);
        v.getConnectionChanges().add(change);
    }

    // ---------------------------------------------------------------
    // Detection
    // ---------------------------------------------------------------

    @Test
    void recordMergeConflicts_SiblingHasADifferentRowForATouchedIdentity_CreatesOneConflictPerIdentity() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        UUID sharedIdentity = UUID.randomUUID();

        // What the merge just left in the parent's snapshot for this identity.
        Well mergedWell = well("Source Well", sharedIdentity);
        editNode(source, mergedWell);
        parent.getNodeSnapshot().add(mergedWell);

        // The sibling's own, different row for the same identity.
        sibling.getNodeSnapshot().add(well("Sibling Well", sharedIdentity));
        editNode(sibling, well("Unrelated Well", UUID.randomUUID()));

        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(source, sibling));
        when(conflictRepository.save(any(VersionConflict.class))).thenAnswer(i -> i.getArgument(0));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertEquals(1, result.size());
        VersionConflict conflict = result.get(0);
        assertEquals(parent, conflict.getMergedVersion());
        assertEquals(source, conflict.getSourceVersion());
        assertEquals(sibling, conflict.getConflictingVersion());
        assertEquals(ConflictEntityType.NODE, conflict.getEntityType());
        assertEquals(sharedIdentity, conflict.getIdentityId());
        assertFalse(conflict.isResolved());
    }

    @Test
    void recordMergeConflicts_SiblingHasADifferentConnectionForATouchedIdentity_CreatesConflict() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        UUID sharedIdentity = UUID.randomUUID();

        NodeConnection mergedConnection = connection(sharedIdentity, UUID.randomUUID(), UUID.randomUUID());
        editConnection(source, mergedConnection);
        parent.getConnectionSnapshot().add(mergedConnection);

        sibling.getConnectionSnapshot().add(connection(sharedIdentity, UUID.randomUUID(), UUID.randomUUID()));

        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(sibling));
        when(conflictRepository.save(any(VersionConflict.class))).thenAnswer(i -> i.getArgument(0));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertEquals(1, result.size());
        assertEquals(ConflictEntityType.CONNECTION, result.get(0).getEntityType());
        assertEquals(sharedIdentity, result.get(0).getIdentityId());
    }

    @Test
    void recordMergeConflicts_SiblingNeverTouchedIdentityButHasNoRowForIt_StillCreatesConflict() {
        // The user's other scenario: the merged branch added a brand-new node.
        // A sibling that never touched this identity at all still needs to be
        // flagged, since its own static snapshot simply doesn't have it.
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        UUID newIdentity = UUID.randomUUID();

        Well addedWell = well("Newly Added Well", newIdentity);
        editNode(source, addedWell);
        parent.getNodeSnapshot().add(addedWell);
        // sibling has no NodeChange and no snapshot entry for newIdentity at all.

        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(sibling));
        when(conflictRepository.save(any(VersionConflict.class))).thenAnswer(i -> i.getArgument(0));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertEquals(1, result.size());
        assertEquals(ConflictEntityType.NODE, result.get(0).getEntityType());
        assertEquals(newIdentity, result.get(0).getIdentityId());
    }

    @Test
    void recordMergeConflicts_SiblingAlreadyHasTheIdenticalMergedRow_CreatesNothing() {
        // e.g. the sibling was branched off AFTER an earlier merge already
        // applied this identity, so it inherited the exact same row - no
        // divergence to flag.
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        UUID sharedIdentity = UUID.randomUUID();

        Well mergedWell = well("Merged Well", sharedIdentity);
        editNode(source, mergedWell);
        parent.getNodeSnapshot().add(mergedWell);
        sibling.getNodeSnapshot().add(mergedWell); // same row, inherited untouched

        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(sibling));

        assertTrue(service.recordMergeConflicts(parent, source).isEmpty());
        verify(conflictRepository, never()).save(any());
    }

    @Test
    void recordMergeConflicts_SiblingChangedDifferentIdentity_CreatesNothing() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        editNode(source, well("Source Well", UUID.randomUUID()));
        editNode(sibling, well("Sibling Well", UUID.randomUUID()));
        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(sibling));

        assertTrue(service.recordMergeConflicts(parent, source).isEmpty());
        verify(conflictRepository, never()).save(any());
    }

    @Test
    void recordMergeConflicts_SourceWithoutChanges_DoesNotQuerySiblings() {
        assertTrue(service.recordMergeConflicts(version("parent"), version("source")).isEmpty());
        verify(versionRepository, never()).findByParentVersionId(any());
    }

    // ---------------------------------------------------------------
    // Node resolution
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_NodeAccept_BothHaveIt_CopiesDataKeepsSiblingId() {
        UUID identityId = UUID.randomUUID();
        Well parentWell = well("Parent Well", identityId);
        Well siblingWell = well("Sibling Well", identityId);
        UUID siblingWellId = siblingWell.getId();

        Version parent = version("parent");
        parent.getNodeSnapshot().add(parentWell);
        Version sibling = version("sibling");
        sibling.getNodeSnapshot().add(siblingWell);
        editNode(sibling, siblingWell);

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);
        lenient().when(nodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        VersionConflictDTO result = service.resolveConflict(conflict.getId(), ConflictResolutionType.ACCEPT);

        assertTrue(result.isResolved());
        assertEquals("Parent Well", siblingWell.getName());
        assertEquals(siblingWellId, siblingWell.getId());
        assertTrue(sibling.getNodeChanges().isEmpty());
        assertEquals(1, sibling.getNodeSnapshot().size());
        verify(versionRepository).save(sibling);
    }

    @Test
    void resolveConflict_NodeAccept_OnlyParentHasIt_ClonesIntoSiblingSnapshot() {
        UUID identityId = UUID.randomUUID();
        Well parentWell = well("Parent Well", identityId);

        Version parent = version("parent");
        parent.getNodeSnapshot().add(parentWell);
        Version sibling = version("sibling");

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);
        when(nodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolveConflict(conflict.getId(), ConflictResolutionType.ACCEPT);

        assertEquals(1, sibling.getNodeSnapshot().size());
        Well cloned = (Well) sibling.getNodeSnapshot().get(0);
        assertEquals(identityId, cloned.getIdentityId());
        assertEquals("Parent Well", cloned.getName());
        assertTrue(cloned.getId() == null || !cloned.getId().equals(parentWell.getId()));
    }

    @Test
    void resolveConflict_NodeAccept_OnlySiblingHasIt_RemovesFromSnapshot() {
        UUID identityId = UUID.randomUUID();
        Well siblingWell = well("Sibling Well", identityId);

        Version parent = version("parent");
        Version sibling = version("sibling");
        sibling.getNodeSnapshot().add(siblingWell);
        editNode(sibling, siblingWell);

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        service.resolveConflict(conflict.getId(), ConflictResolutionType.ACCEPT);

        assertTrue(sibling.getNodeSnapshot().isEmpty());
        assertTrue(sibling.getNodeChanges().isEmpty());
        verify(nodeRepository, never()).save(any());
    }

    @Test
    void resolveConflict_NodeReject_BothHaveIt_WritesEditChange() {
        UUID identityId = UUID.randomUUID();
        Well parentWell = well("Parent Well", identityId);
        Well siblingWell = well("Sibling Well", identityId);

        Version parent = version("parent");
        parent.getNodeSnapshot().add(parentWell);
        Version sibling = version("sibling");
        sibling.getNodeSnapshot().add(siblingWell);
        editNode(sibling, siblingWell);

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        service.resolveConflict(conflict.getId(), ConflictResolutionType.REJECT);

        assertEquals(1, sibling.getNodeChanges().size());
        NodeChange change = sibling.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.EDIT, change.getChangeType());
        assertEquals(parentWell, change.getChangedNode());
        assertEquals(siblingWell, change.getResultNode());
        assertTrue(sibling.getNodeSnapshot().contains(siblingWell));
    }

    @Test
    void resolveConflict_NodeReject_OnlyParentHasIt_WritesDeleteChange() {
        // The user's example: a node was added to the parent, but the sibling rejects it.
        UUID identityId = UUID.randomUUID();
        Well parentWell = well("Parent Well", identityId);

        Version parent = version("parent");
        parent.getNodeSnapshot().add(parentWell);
        Version sibling = version("sibling");

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        service.resolveConflict(conflict.getId(), ConflictResolutionType.REJECT);

        assertEquals(1, sibling.getNodeChanges().size());
        NodeChange change = sibling.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.DELETE, change.getChangeType());
        assertEquals(parentWell, change.getChangedNode());
        assertNull(change.getResultNode());
        assertTrue(sibling.getNodeSnapshot().isEmpty());
    }

    @Test
    void resolveConflict_NodeReject_OnlySiblingHasIt_WritesAddChange() {
        UUID identityId = UUID.randomUUID();
        Well siblingWell = well("Sibling Well", identityId);

        Version parent = version("parent");
        Version sibling = version("sibling");
        sibling.getNodeSnapshot().add(siblingWell);
        editNode(sibling, siblingWell);

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.NODE, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        service.resolveConflict(conflict.getId(), ConflictResolutionType.REJECT);

        assertEquals(1, sibling.getNodeChanges().size());
        NodeChange change = sibling.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.ADD, change.getChangeType());
        assertNull(change.getChangedNode());
        assertEquals(siblingWell, change.getResultNode());
        assertTrue(sibling.getNodeSnapshot().contains(siblingWell));
    }

    // ---------------------------------------------------------------
    // Connection resolution
    // ---------------------------------------------------------------

    @Test
    void resolveConflict_ConnectionAccept_BothHaveIt_RemapsEndpointsThroughIdentity() {
        UUID connIdentity = UUID.randomUUID();
        UUID fromIdentity = UUID.randomUUID();
        UUID toIdentity = UUID.randomUUID();

        Well parentFrom = well("From", fromIdentity);
        Well parentTo = well("To", toIdentity);
        Well siblingFrom = well("From", fromIdentity);
        Well siblingTo = well("To", toIdentity);

        Version parent = version("parent");
        parent.getNodeSnapshot().add(parentFrom);
        parent.getNodeSnapshot().add(parentTo);
        NodeConnection parentConnection = connection(connIdentity, parentFrom.getId(), parentTo.getId());
        parent.getConnectionSnapshot().add(parentConnection);

        Version sibling = version("sibling");
        sibling.getNodeSnapshot().add(siblingFrom);
        sibling.getNodeSnapshot().add(siblingTo);
        NodeConnection siblingConnection = connection(connIdentity, UUID.randomUUID(), UUID.randomUUID());
        sibling.getConnectionSnapshot().add(siblingConnection);
        editConnection(sibling, siblingConnection);

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.CONNECTION, connIdentity);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);
        when(connectionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resolveConflict(conflict.getId(), ConflictResolutionType.ACCEPT);

        assertEquals(siblingFrom.getId(), siblingConnection.getFromNodeId());
        assertEquals(siblingTo.getId(), siblingConnection.getToNodeId());
        assertTrue(sibling.getConnectionChanges().isEmpty());
    }

    @Test
    void resolveConflict_ConnectionReject_OnlyParentHasIt_WritesDeleteChange() {
        UUID identityId = UUID.randomUUID();
        NodeConnection parentConnection = connection(identityId, UUID.randomUUID(), UUID.randomUUID());

        Version parent = version("parent");
        parent.getConnectionSnapshot().add(parentConnection);
        Version sibling = version("sibling");

        VersionConflict conflict = conflictFixture(parent, sibling, ConflictEntityType.CONNECTION, identityId);
        when(conflictRepository.findById(conflict.getId())).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        service.resolveConflict(conflict.getId(), ConflictResolutionType.REJECT);

        assertEquals(1, sibling.getConnectionChanges().size());
        ConnectionChange change = sibling.getConnectionChanges().get(0);
        assertEquals(ChangeTypeEnum.DELETE, change.getChangeType());
        assertEquals(parentConnection, change.getChangedConnection());
    }

    // ---------------------------------------------------------------
    // Listing / errors
    // ---------------------------------------------------------------

    @Test
    void getConflictsForVersion_ReturnsMappedConflicts() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        VersionConflict conflict = new VersionConflict(parent, source, sibling, ConflictEntityType.NODE,
                UUID.randomUUID());
        when(versionRepository.existsById(sibling.getId())).thenReturn(true);
        when(conflictRepository.findByConflictingVersionId(sibling.getId())).thenReturn(List.of(conflict));

        List<VersionConflictDTO> result = service.getConflictsForVersion(sibling.getId());

        assertEquals(1, result.size());
        assertEquals(sibling.getId(), result.get(0).getConflictingVersionId());
        assertEquals(source.getId(), result.get(0).getSourceVersionId());
        assertFalse(result.get(0).isResolved());
    }

    @Test
    void getConflictsForVersion_UnknownVersion_Throws() {
        UUID id = UUID.randomUUID();
        when(versionRepository.existsById(id)).thenReturn(false);

        assertThrows(VersionNotFoundException.class, () -> service.getConflictsForVersion(id));
    }

    @Test
    void resolveConflict_UnknownConflict_Throws() {
        UUID id = UUID.randomUUID();
        when(conflictRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> service.resolveConflict(id, ConflictResolutionType.ACCEPT));
    }

    private VersionConflict conflictFixture(Version parent, Version sibling, ConflictEntityType type, UUID identityId) {
        VersionConflict conflict = new VersionConflict(parent, version("source"), sibling, type, identityId);
        ReflectionTestUtils.setField(conflict, "id", UUID.randomUUID());
        return conflict;
    }
}
