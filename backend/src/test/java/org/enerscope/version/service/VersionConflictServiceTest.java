package org.enerscope.version.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.version.dto.VersionConflictDTO;
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
    private AppLogger logger;

    private VersionConflictService service;

    @BeforeEach
    void setUp() {
        service = new VersionConflictService(versionRepository, conflictRepository, logger);
    }

    private Version version(String name) {
        Version v = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        ReflectionTestUtils.setField(v, "id", UUID.randomUUID());
        return v;
    }

    private Well node() {
        Well well = new Well();
        ReflectionTestUtils.setField(well, "id", UUID.randomUUID());
        return well;
    }

    private NodeConnection connection() {
        NodeConnection connection = new NodeConnection();
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        return connection;
    }

    private void editNode(Version v, Well node) {
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

    @Test
    void recordMergeConflicts_SiblingChangedSameNode_CreatesConflict() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        Well shared = node();
        editNode(source, shared);
        editNode(sibling, shared);
        editNode(sibling, node());
        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(source, sibling));
        when(conflictRepository.save(any(VersionConflict.class))).thenAnswer(i -> i.getArgument(0));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertEquals(1, result.size());
        VersionConflict conflict = result.get(0);
        assertEquals(parent, conflict.getMergedVersion());
        assertEquals(source, conflict.getSourceVersion());
        assertEquals(sibling, conflict.getConflictingVersion());
        assertEquals(Set.of(shared.getId()), conflict.getConflictingNodeIds());
        assertTrue(conflict.getConflictingConnectionIds().isEmpty());
        assertFalse(conflict.isResolved());
    }

    @Test
    void recordMergeConflicts_SiblingChangedSameConnection_CreatesConflict() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        NodeConnection shared = connection();
        editConnection(source, shared);
        editConnection(sibling, shared);
        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(sibling));
        when(conflictRepository.save(any(VersionConflict.class))).thenAnswer(i -> i.getArgument(0));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertEquals(1, result.size());
        assertEquals(Set.of(shared.getId()), result.get(0).getConflictingConnectionIds());
    }

    @Test
    void recordMergeConflicts_SiblingChangedDifferentEntities_CreatesNothing() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        editNode(source, node());
        editNode(sibling, node());
        when(versionRepository.findByParentVersionId(parent.getId())).thenReturn(List.of(source, sibling));

        List<VersionConflict> result = service.recordMergeConflicts(parent, source);

        assertTrue(result.isEmpty());
        verify(conflictRepository, never()).save(any());
    }

    @Test
    void recordMergeConflicts_SourceWithoutChanges_DoesNotQuerySiblings() {
        Version parent = version("parent");
        Version source = version("source");

        assertTrue(service.recordMergeConflicts(parent, source).isEmpty());

        verify(versionRepository, never()).findByParentVersionId(any());
    }

    @Test
    void getConflictsForVersion_ReturnsMappedConflicts() {
        Version parent = version("parent");
        Version source = version("source");
        Version sibling = version("sibling");
        VersionConflict conflict = new VersionConflict(parent, source, sibling, Set.of(UUID.randomUUID()), Set.of());
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
    void resolveConflict_MarksResolved() {
        Version parent = version("parent");
        VersionConflict conflict = new VersionConflict(parent, version("source"), version("sibling"), Set.of(),
                Set.of());
        UUID id = UUID.randomUUID();
        when(conflictRepository.findById(id)).thenReturn(Optional.of(conflict));
        when(conflictRepository.save(conflict)).thenReturn(conflict);

        VersionConflictDTO result = service.resolveConflict(id);

        assertTrue(result.isResolved());
        assertTrue(conflict.getResolvedAt() != null);
    }

    @Test
    void resolveConflict_UnknownConflict_Throws() {
        UUID id = UUID.randomUUID();
        when(conflictRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> service.resolveConflict(id));
    }
}
