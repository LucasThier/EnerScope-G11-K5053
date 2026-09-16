package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.ConnectionDTO;
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
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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

    /**
     * Mocked rather than built over mocked repositories: the rules it carries
     * are covered case by case in {@code ProjectAccessGuardTest}, and the tests
     * below are about version mechanics. What matters here is that every entry
     * point runs it, which the authorization tests at the bottom assert
     * explicitly.
     */
    @Mock
    private org.enerscope.project.service.ProjectAccessGuard accessGuard;

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
                nodeService,
                accessGuard);
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

    // ---- authorization -------------------------------------------------------
    //
    // Every entry point of this service is reachable from VersionController with
    // nothing but a version UUID, so each one has to resolve the owning project
    // and check the caller against it. The rules themselves live in
    // ProjectAccessGuard (and are tested there); what these cases pin down is
    // that no entry point skips the guard, and that a rejected call touches
    // neither the database nor the node service.

    private static final UUID GUARDED_VERSION_ID = UUID.randomUUID();
    private static final UUID GUARDED_NODE_ID = UUID.randomUUID();
    private static final UUID GUARDED_CONNECTION_ID = UUID.randomUUID();

    /** The eight endpoints that change a version, or something inside it. */
    static Stream<Arguments> mutatingOperations() {
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        ConnectionDTO connectionDTO = new ConnectionDTO();
        org.enerscope.version.dto.VersionDTO versionDTO = new org.enerscope.version.dto.VersionDTO();
        versionDTO.setName("Renamed");
        return Stream.of(
                arguments("deleteVersion",
                        (Consumer<VersionService>) s -> s.deleteVersion(GUARDED_VERSION_ID)),
                arguments("modifyVersion",
                        (Consumer<VersionService>) s -> s.modifyVersion(GUARDED_VERSION_ID, versionDTO)),
                arguments("addNodeToVersion",
                        (Consumer<VersionService>) s -> s.addNodeToVersion(GUARDED_VERSION_ID, wellDTO)),
                arguments("addConnectionToVersion",
                        (Consumer<VersionService>) s -> s.addConnectionToVersion(GUARDED_VERSION_ID, connectionDTO)),
                arguments("editNodeInVersion",
                        (Consumer<VersionService>) s -> s.editNodeInVersion(
                                GUARDED_VERSION_ID, GUARDED_NODE_ID, wellDTO)),
                arguments("editConnectionInVersion",
                        (Consumer<VersionService>) s -> s.editConnectionInVersion(
                                GUARDED_VERSION_ID, GUARDED_CONNECTION_ID, connectionDTO)),
                arguments("deleteNodeFromVersion",
                        (Consumer<VersionService>) s -> s.deleteNodeFromVersion(GUARDED_VERSION_ID, GUARDED_NODE_ID)),
                arguments("deleteConnectionFromVersion",
                        (Consumer<VersionService>) s -> s.deleteConnectionFromVersion(
                                GUARDED_VERSION_ID, GUARDED_CONNECTION_ID)));
    }

    @ParameterizedTest(name = "{0} is refused without EDIT_PROJECT on the owning project")
    @MethodSource("mutatingOperations")
    void mutatingOperationsRejectCallerWithoutEditPermission(String name, Consumer<VersionService> operation) {
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(accessGuard).assertCanEditVersion(GUARDED_VERSION_ID);

        assertThrows(ForbiddenException.class, () -> operation.accept(versionService));

        verifyNoInteractions(versionRepository, nodeRepository, connectionRepository, nodeService);
    }

    @ParameterizedTest(name = "{0} is refused without a session")
    @MethodSource("mutatingOperations")
    void mutatingOperationsRejectUnauthenticatedCaller(String name, Consumer<VersionService> operation) {
        doThrow(new UnauthorizedException("Authentication required"))
                .when(accessGuard).assertCanEditVersion(GUARDED_VERSION_ID);

        assertThrows(UnauthorizedException.class, () -> operation.accept(versionService));

        verifyNoInteractions(versionRepository, nodeRepository, connectionRepository, nodeService);
    }

    @Test
    void getVersionChecksViewPermissionAndReturnsTheVersion() {
        Version version = new Version("Baseline", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        when(versionRepository.findById(GUARDED_VERSION_ID)).thenReturn(Optional.of(version));

        assertSame(version, versionService.getVersion(GUARDED_VERSION_ID));

        verify(accessGuard).assertCanViewVersion(GUARDED_VERSION_ID);
    }

    @Test
    void getVersionRejectsCallerOutsideTheOwningProject() {
        doThrow(new ForbiddenException("You are not allowed to view this project"))
                .when(accessGuard).assertCanViewVersion(GUARDED_VERSION_ID);

        assertThrows(ForbiddenException.class, () -> versionService.getVersion(GUARDED_VERSION_ID));

        verifyNoInteractions(versionRepository);
    }

    @Test
    void getVersionRejectsUnauthenticatedCaller() {
        doThrow(new UnauthorizedException("Authentication required"))
                .when(accessGuard).assertCanViewVersion(GUARDED_VERSION_ID);

        assertThrows(UnauthorizedException.class, () -> versionService.getVersion(GUARDED_VERSION_ID));

        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveOrphanVersionCreatesTheVersionForAPlatformAdmin() {
        org.enerscope.version.dto.VersionDTO data = new org.enerscope.version.dto.VersionDTO();
        data.setName("Scratch");
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        Version saved = versionService.saveOrphanVersion(data);

        assertEquals("Scratch", saved.getName());
        verify(accessGuard).assertIsPlatformAdmin("create detached versions");
    }

    @Test
    void saveOrphanVersionRejectsNonPlatformAdmin() {
        org.enerscope.version.dto.VersionDTO data = new org.enerscope.version.dto.VersionDTO();
        data.setName("Scratch");
        doThrow(new ForbiddenException("Only platform admins can create detached versions"))
                .when(accessGuard).assertIsPlatformAdmin("create detached versions");

        assertThrows(ForbiddenException.class, () -> versionService.saveOrphanVersion(data));

        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveOrphanVersionRejectsUnauthenticatedCaller() {
        org.enerscope.version.dto.VersionDTO data = new org.enerscope.version.dto.VersionDTO();
        data.setName("Scratch");
        doThrow(new UnauthorizedException("Authentication required"))
                .when(accessGuard).assertIsPlatformAdmin("create detached versions");

        assertThrows(UnauthorizedException.class, () -> versionService.saveOrphanVersion(data));

        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveVersionIsUnguardedBecauseItsCallersAuthorizeInstead() {
        org.enerscope.version.dto.VersionDTO data = new org.enerscope.version.dto.VersionDTO();
        data.setName("Baseline");
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        versionService.saveVersion(data);

        // ProjectService.saveVersion checks EDIT_PROJECT before reaching here, and
        // saveOrphanVersion requires a platform admin; a check in between would be
        // a third rule on a path that has no version id to check yet.
        verifyNoInteractions(accessGuard);
    }
}
