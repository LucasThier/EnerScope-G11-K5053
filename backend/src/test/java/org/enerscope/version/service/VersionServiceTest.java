package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.DiagramDTO;
import org.enerscope.node.dto.GeographicalPositionDTO;
import org.enerscope.node.dto.GraphPositionDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.GeographicalPosition;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private org.enerscope.node.repository.NodeConnectionRepository connectionRepository;

    @Mock
    private org.enerscope.node.repository.BaseNodeRepository nodeRepository;

    @Mock
    private org.enerscope.logging.AppLogger logger;

    @Mock
    private org.enerscope.node.service.NodeService nodeService;

    @InjectMocks
    private VersionService versionService;

    @BeforeEach
    void setUp() {
        // Using mocks for testing
        versionService = new VersionService(
                versionRepository,
                connectionRepository,
                nodeRepository,
                logger,
                nodeService);
    }

    @Test
    void modifyVersionShouldUpdateNameWithoutCreatingNodeChange() {
        // Given
        UUID versionId = UUID.randomUUID();
        String newName = "New Version Name";
        Version existingVersion = new Version("Old Name", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(existingVersion));
        when(versionRepository.save(any(Version.class))).thenReturn(existingVersion);

        // Create a VersionDTO with just the name change
        org.enerscope.version.dto.VersionDTO versionDTO = new org.enerscope.version.dto.VersionDTO();
        versionDTO.setName(newName);

        // When
        Version result = versionService.modifyVersion(versionId, versionDTO);

        // Then
        assertEquals(newName, result.getName());
        assertTrue(result.getNodeChanges().isEmpty()); // No NodeChanges created
        assertTrue(result.getConnectionChanges().isEmpty());
        verify(versionRepository).save(existingVersion);
    }

    @Test
    void editNodeInVersion_WhenNodeAddedInThisVersion_ShouldEditInPlaceAndCreateEditChange() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();
        nodeDTO.setName("Edited Well");
        nodeDTO.setState(NodeStateEnum.RUNNING);

        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        // Create a minimal Well instance for testing
        Well originalNode = new Well(
                "Original Well",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                120, // lifespanInMonths
                MoneyAmount.of(1000000), // upkeepCosts
                30, // maintenanceIntervalInDays
                MoneyAmount.of(50000), // operatingCosts
                0.0f, // wastePercentage
                new InvestmentCost(), // investmentCost
                new NodeGraphData(), // graphData
                nodeId, // identity
                new NodeTypeData(), // type
                100.0f, // maxCollectionCapacity
                0.5f, // decline_curve
                0.8f, // gasRichness
                10, // DTMTime
                MoneyAmount.of(5000), // DTMCost
                500f // surface
        );

        // Add the node to version's snapshot to simulate it being added in this version
        version.getNodeSnapshot().add(originalNode);

        // Mock repository calls
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(originalNode));
        when(versionRepository.save(any(Version.class))).thenReturn(version);

        // Mock NodeService to return the same instance (edited in place)
        when(nodeService.editWell(any(Well.class), any(WellDTO.class))).thenAnswer(invocation -> {
            Well well = invocation.getArgument(0);
            WellDTO dto = invocation.getArgument(1);
            well.setName(dto.getName());
            well.setState(dto.getState());
            return well; // Return same instance, modified
        });

        // When
        BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);

        // Then
        assertNotNull(result);
        assertSame(originalNode, result); // Should return the same instance
        assertEquals("Edited Well", result.getName());
        assertEquals(NodeStateEnum.RUNNING, result.getState());

        // Verify that an EDIT change was created
        assertEquals(1, version.getNodeChanges().size());
        NodeChange nodeChange = version.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.EDIT, nodeChange.getChangeType());
        assertSame(nodeChange.getChangedNode(), result);
        assertSame(nodeChange.getResultNode(), result);

        // Verify that version was saved
        verify(versionRepository, times(1)).save(version);
    }

    @Test
    void editNodeInVersion_WhenNodePreviouslyEditedInThisVersion_ShouldEditInPlaceAndCreateAnotherEditChange() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();
        nodeDTO.setName("Twice Edited Well");
        nodeDTO.setState(NodeStateEnum.PENDING);

        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        // Create a minimal Well instance for testing
        Well originalNode = new Well(
                "Original Well",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                120, // lifespanInMonths
                MoneyAmount.of(1000000), // upkeepCosts
                30, // maintenanceIntervalInDays
                MoneyAmount.of(50000), // operatingCosts
                0.0f, // wastePercentage
                new InvestmentCost(), // investmentCost
                new NodeGraphData(), // graphData
                nodeId, // identity
                new NodeTypeData(), // type
                100.0f, // maxCollectionCapacity
                0.5f, // decline_curve
                0.8f, // gasRichness
                10, // DTMTime
                MoneyAmount.of(5000), // DTMCost
                500f // surface
        );

        // Add the node to version's snapshot
        version.getNodeSnapshot().add(originalNode);

        // Add a previous EDIT change to simulate the node was already edited in this
        // version
        NodeChange previousEditChange = new NodeChange();
        previousEditChange.setChangeType(ChangeTypeEnum.EDIT);
        previousEditChange.setChangedNode(originalNode);
        previousEditChange.setResultNode(originalNode);
        version.getNodeChanges().add(previousEditChange);

        // Mock repository calls
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(originalNode));
        when(versionRepository.save(any(Version.class))).thenReturn(version);

        // Mock NodeService to return the same instance (edited in place)
        when(nodeService.editWell(any(Well.class), any(WellDTO.class))).thenAnswer(invocation -> {
            Well well = invocation.getArgument(0);
            WellDTO dto = invocation.getArgument(1);
            well.setName(dto.getName());
            well.setState(dto.getState());
            return well; // Return same instance, modified
        });

        // When
        BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);

        // Then
        assertNotNull(result);
        assertSame(originalNode, result); // Should return the same instance
        assertEquals("Twice Edited Well", result.getName());
        assertEquals(NodeStateEnum.PENDING, result.getState());

        // Verify that only one EDIT change was created (total 2 changes)
        assertEquals(1, version.getNodeChanges().size());
        NodeChange latestChange = version.getNodeChanges().get(0); // Get the most recent change
        assertEquals(ChangeTypeEnum.EDIT, latestChange.getChangeType());
        assertSame(latestChange.getChangedNode(), result);
        assertSame(latestChange.getResultNode(), result);

        // Verify that version was saved
        verify(versionRepository, times(1)).save(version);
    }

    @Test
    void editNodeInVersion_WhenNodeCameFromParent_ShouldUpdateSnapshotAndCreateEditChange() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();
        nodeDTO.setName("Edited From Parent");
        nodeDTO.setState(NodeStateEnum.REMOVED);

        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        // Create a minimal Well instance for testing
        Well originalNode = new Well(
                "Original Well",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                120, // lifespanInMonths
                MoneyAmount.of(1000000), // upkeepCosts
                30, // maintenanceIntervalInDays
                MoneyAmount.of(50000), // operatingCosts
                0.0f, // wastePercentage
                new InvestmentCost(), // investmentCost
                new NodeGraphData(), // graphData
                nodeId, // identity
                new NodeTypeData(), // type
                100.0f, // maxCollectionCapacity
                0.5f, // decline_curve
                0.8f, // gasRichness
                10, // DTMTime
                MoneyAmount.of(5000), // DTMCost
                500f // surface

        );

        // Do NOT add the node to version's snapshot to simulate it coming from parent
        // version.getNodeSnapshot().add(originalNode); // Intentionally left out

        // Mock repository calls
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(originalNode));
        when(versionRepository.save(any(Version.class))).thenReturn(version);

        // Mock NodeService to return the same instance (edited in place)
        when(nodeService.editWell(any(Well.class), any(WellDTO.class))).thenAnswer(invocation -> {
            Well well = invocation.getArgument(0);
            WellDTO dto = invocation.getArgument(1);
            well.setName(dto.getName());
            well.setState(dto.getState());
            return well; // Return same instance, modified
        });

        // When
        BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);

        // Then
        assertNotNull(result);
        assertSame(originalNode, result); // Should return the same instance
        assertEquals("Edited From Parent", result.getName());
        assertEquals(NodeStateEnum.REMOVED, result.getState());

        // Verify that the snapshot was updated (node removed and re-added)
        assertTrue(version.getNodeSnapshot().contains(originalNode));
        assertEquals(1, version.getNodeSnapshot().size());

        // Verify that an EDIT change was created
        assertEquals(1, version.getNodeChanges().size());
        NodeChange nodeChange = version.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.EDIT, nodeChange.getChangeType());

        // Verify that version was saved
        verify(versionRepository, times(1)).save(version);
    }

    @Test
    void editNodeInVersion_WhenNodeDTOIsNull_ShouldThrowNullPointerException() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        // When/Then
        assertThrows(NullPointerException.class, () -> versionService.editNodeInVersion(versionId, nodeId, null));
    }

    @Test
    void editNodeInVersion_WhenNodeIdIsNull_ShouldThrowNullPointerException() {
        // Given
        UUID versionId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();

        // When/Then
        assertThrows(NullPointerException.class, () -> versionService.editNodeInVersion(versionId, null, nodeDTO));
    }

    @Test
    void editNodeInVersion_WhenVersionNotFound_ShouldThrowVersionNotFoundException() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();

        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(VersionNotFoundException.class,
                () -> versionService.editNodeInVersion(versionId, nodeId, nodeDTO));
    }

    @Test
    void editNodeInVersion_WhenNodeNotFound_ShouldThrowEntityNotFoundException() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        WellDTO nodeDTO = new WellDTO();

        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(EntityNotFoundException.class, () -> versionService.editNodeInVersion(versionId, nodeId, nodeDTO));
    }

    @Test
    void addNodeToVersion_WithWellDTO_ShouldAddWellToVersion() {
        // Given
        UUID versionId = UUID.randomUUID();
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        wellDTO.setState(NodeStateEnum.PROPOSED);

        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        Well savedWell = new Well(
                "Test Well",
                NodeStateEnum.PROPOSED,
                Instant.now(),
                120, // lifespanInMonths
                MoneyAmount.of(1000000), // upkeepCosts
                30, // maintenanceIntervalInDays
                MoneyAmount.of(50000), // operatingCosts
                0.0f, // wastePercentage
                new InvestmentCost(), // investmentCost
                new NodeGraphData(), // graphData
                UUID.randomUUID(), // identity
                new NodeTypeData(), // type
                100.0f, // maxCollectionCapacity
                0.5f, // decline_curve
                0.8f, // gasRichness
                10, // DTMTime
                MoneyAmount.of(5000), // DTMCost
                500f // surface
        );

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeService.saveWell(any(WellDTO.class))).thenReturn(savedWell);
        when(versionRepository.save(any(Version.class))).thenReturn(version);

        // When
        BaseNode result = versionService.addNodeToVersion(versionId, wellDTO);

        // Then
        assertNotNull(result);
        assertEquals("Test Well", result.getName());
        assertEquals(NodeStateEnum.PROPOSED, result.getState());
        assertTrue(version.getNodeSnapshot().contains(savedWell));
        assertEquals(1, version.getNodeChanges().size());

        NodeChange nodeChange = version.getNodeChanges().get(0);
        assertEquals(ChangeTypeEnum.ADD, nodeChange.getChangeType());

        verify(versionRepository, times(1)).save(version);
    }

    @Test
    void getDiagram_ShouldMapNodesAndConnectionsToDTOs() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();

        Version version = new Version("V1", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());

        NodeGraphData graphData = new NodeGraphData(
                new GraphPosition(10.0, 20.0),
                new GeographicalPosition(-70.0, -34.0));
        Well well = new Well(
                "Well A", NodeStateEnum.RUNNING, Instant.now(), 12,
                MoneyAmount.of(1), 30, MoneyAmount.of(1), 0.0f,
                new InvestmentCost(), graphData, UUID.randomUUID(),
                new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                1.0f, 1.0f, 0.5f, 1, MoneyAmount.of(1), 1.0f);
        version.getNodeSnapshot().add(well);

        NodeConnection connection = new NodeConnection(UUID.randomUUID(), fromId, toId);
        version.getConnectionSnapshot().add(connection);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        // When
        DiagramDTO diagram = versionService.getDiagram(versionId);

        // Then
        assertEquals(versionId, diagram.getVersionId());
        assertEquals(1, diagram.getNodes().size());
        assertEquals("Well A", diagram.getNodes().get(0).getName());
        assertEquals(NodeTypeEnum.WELL, diagram.getNodes().get(0).getType().getNodeType());
        assertEquals(10.0, diagram.getNodes().get(0).getGraphData().getGraphPosition().getX());
        assertEquals(-70.0, diagram.getNodes().get(0).getGraphData().getGeographicalPosition().getLongitude());
        assertEquals(1, diagram.getConnections().size());
        assertEquals(fromId, diagram.getConnections().get(0).getFromNodeId());
        assertEquals(toId, diagram.getConnections().get(0).getToNodeId());
    }

    @Test
    void updateNodePosition_ShouldUpdateGraphAndGeographicalPosition() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        Version version = new Version("V1", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        Well node = new Well(
                "Well A", NodeStateEnum.RUNNING, Instant.now(), 12,
                MoneyAmount.of(1), 30, MoneyAmount.of(1), 0.0f,
                new InvestmentCost(), new NodeGraphData(), nodeId,
                new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                1.0f, 1.0f, 0.5f, 1, MoneyAmount.of(1), 1.0f);
        version.getNodeSnapshot().add(node);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.save(any(BaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NodeGraphDataDTO position = new NodeGraphDataDTO(
                new GraphPositionDTO(5.0, 6.0),
                new GeographicalPositionDTO(-58.0, -34.0));

        // When
        BaseNode result = versionService.updateNodePosition(versionId, nodeId, position);

        // Then
        assertEquals(5.0, result.getGraphData().getGraphPosition().getX());
        assertEquals(6.0, result.getGraphData().getGraphPosition().getY());
        assertEquals(-58.0, result.getGraphData().getGeographicalPosition().getLongitude());
        assertEquals(-34.0, result.getGraphData().getGeographicalPosition().getLatitude());
        verify(nodeRepository, times(1)).save(node);
    }

    @Test
    void saveVersion_WithoutParent_InitialisesEmptyNonNullSnapshots() {
        // Given
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));
        org.enerscope.version.dto.VersionDTO dto = new org.enerscope.version.dto.VersionDTO();
        dto.setName("V1");

        // When
        Version saved = versionService.saveVersion(dto);

        // Then: snapshots must be empty (not null) so the node/connection ABM can append.
        assertNotNull(saved.getNodeSnapshot());
        assertTrue(saved.getNodeSnapshot().isEmpty());
        assertNotNull(saved.getConnectionSnapshot());
        assertTrue(saved.getConnectionSnapshot().isEmpty());
    }

    @Test
    void updateNodeBasics_ShouldUpdateNameAndState() {
        // Given
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();

        Version version = new Version("V1", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        Well node = new Well(
                "Old Name", NodeStateEnum.PROPOSED, Instant.now(), 12,
                MoneyAmount.of(1), 30, MoneyAmount.of(1), 0.0f,
                new InvestmentCost(), new NodeGraphData(), nodeId,
                new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                1.0f, 1.0f, 0.5f, 1, MoneyAmount.of(1), 1.0f);
        version.getNodeSnapshot().add(node);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));
        when(nodeRepository.save(any(BaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        org.enerscope.node.dto.NodeBasicsDTO basics =
                new org.enerscope.node.dto.NodeBasicsDTO("New Name", NodeStateEnum.RUNNING);

        // When
        BaseNode result = versionService.updateNodeBasics(versionId, nodeId, basics);

        // Then
        assertEquals("New Name", result.getName());
        assertEquals(NodeStateEnum.RUNNING, result.getState());
        verify(nodeRepository, times(1)).save(node);
    }

}