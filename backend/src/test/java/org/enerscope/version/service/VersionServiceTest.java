package org.enerscope.version.service;

<<<<<<< HEAD
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
=======
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.dto.CreateVersionRequestDTO;
>>>>>>> master
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
<<<<<<< HEAD
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
=======
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

>>>>>>> master
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
<<<<<<< HEAD
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
=======
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
>>>>>>> master
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private VersionRepository versionRepository;
<<<<<<< HEAD

    @Mock
    private org.enerscope.node.repository.NodeConnectionRepository connectionRepository;

    @Mock
    private org.enerscope.node.repository.BaseNodeRepository nodeRepository;

    @Mock
    private org.enerscope.logging.AppLogger logger;

    @Mock
    private org.enerscope.node.service.NodeService nodeService;

    @InjectMocks
=======
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AppLogger logger;

>>>>>>> master
    private VersionService versionService;

    @BeforeEach
    void setUp() {
<<<<<<< HEAD
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
        assertSame(nodeChange.getChangedNodeId(), result.getId());
        assertSame(nodeChange.getResultNodeId(), result.getId());

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
        previousEditChange.setChangedNodeId(originalNode.getId());
        previousEditChange.setResultNodeId(originalNode.getId());
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

        // Verify that another EDIT change was created (total 2 changes)
        assertEquals(2, version.getNodeChanges().size());
        NodeChange latestChange = version.getNodeChanges().get(0); // Get the most recent change
        assertEquals(ChangeTypeEnum.EDIT, latestChange.getChangeType());
        assertSame(latestChange.getChangedNodeId(), result.getId());
        assertSame(latestChange.getResultNodeId(), result.getId());

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

}
=======
        versionService = new VersionService(versionRepository, projectRepository, logger);
    }

    // ---- createVersion -------------------------------------------------------

    @Test
    void createVersionPersistsAndLinksToProjectWithoutParent() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        Version saved = versionService.createVersion(projectId, new CreateVersionRequestDTO("v1", null));

        assertEquals("v1", saved.getName());
        assertEquals(project, saved.getProject());
        assertNull(saved.getParentVersion());
        assertTrue(project.getVersions().contains(saved));
    }

    @Test
    void createVersionPersistsWithValidParentVersion() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        ReflectionTestUtils.setField(project, "id", projectId);
        Version parent = new Version("v1", project, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        Version saved = versionService.createVersion(projectId, new CreateVersionRequestDTO("v2", parentId));

        assertEquals(parent, saved.getParentVersion());
    }

    @Test
    void createVersionRejectsUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v1", null)));
        verify(versionRepository, never()).save(any());
    }

    @Test
    void createVersionRejectsUnknownParentVersion() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v2", parentId)));
        verify(versionRepository, never()).save(any());
    }

    @Test
    void createVersionRejectsParentVersionFromDifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        ReflectionTestUtils.setField(project, "id", projectId);
        Project otherProject = new Project("Other", "Other project", new Organization("Acme"));
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());
        Version parent = new Version("v1", otherProject, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v2", parentId)));
        verify(versionRepository, never()).save(any());
    }
}
>>>>>>> master
