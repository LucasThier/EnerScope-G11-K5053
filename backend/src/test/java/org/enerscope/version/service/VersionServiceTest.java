package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.DiagramDTO;
import org.enerscope.node.dto.GeographicalPositionDTO;
import org.enerscope.node.dto.GraphPositionDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.dto.ConnectionDTO;
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
import static org.junit.jupiter.api.Assertions.assertNotSame;
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

        @Mock
        private VersionConflictService versionConflictService;

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
                                versionConflictService);
        }

        @Test
        void modifyVersionShouldUpdateNameWithoutCreatingNodeChange() {
                // Given
                UUID versionId = UUID.randomUUID();
                String newName = "New Version Name";
                Version existingVersion = new Version("Old Name", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
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
        void editNodeInVersion_WhenNodeAddedInThisVersion_ShouldEditInPlaceAndKeepSingleAddChange() {
                // Given
                UUID versionId = UUID.randomUUID();
                UUID nodeId = UUID.randomUUID();
                WellDTO nodeDTO = new WellDTO();
                nodeDTO.setName("Edited Well");
                nodeDTO.setState(NodeStateEnum.RUNNING);

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
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
                                new ConstantValue(0.5f), // decline_curve
                                0.8f, // gasRichness
                                10, // DTMTime
                                MoneyAmount.of(5000), // DTMCost
                                500f // surface
                );

                // Simulate the node having been added in this version: it's in the
                // snapshot, and there's an ADD NodeChange for it (as addNodeToVersion
                // creates - changedNode stays null, only resultNode is set).
                version.getNodeSnapshot().add(originalNode);
                NodeChange addChange = new NodeChange();
                addChange.setChangeType(ChangeTypeEnum.ADD);
                addChange.setResultNode(originalNode);
                version.getNodeChanges().add(addChange);

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

                // Then: this version already owns the node (it added it), so it's safe to
                // mutate in place - no need to clone, and no new NodeChange is created.
                assertNotNull(result);
                assertSame(originalNode, result); // Should return the same instance
                assertEquals("Edited Well", result.getName());
                assertEquals(NodeStateEnum.RUNNING, result.getState());

                assertEquals(1, version.getNodeChanges().size());
                NodeChange nodeChange = version.getNodeChanges().get(0);
                assertSame(addChange, nodeChange); // still the original ADD record
                assertEquals(ChangeTypeEnum.ADD, nodeChange.getChangeType());
                assertSame(nodeChange.getResultNode(), result); // now reflects the edit

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

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
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
                                new ConstantValue(0.5f), // decline_curve
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

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
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
                                new ConstantValue(0.5f), // decline_curve
                                0.8f, // gasRichness
                                10, // DTMTime
                                MoneyAmount.of(5000), // DTMCost
                                500f // surface

                );

                // A child's snapshot starts as a copy of the parent's references, so the
                // inherited (not-yet-edited) node IS present in this version's own
                // snapshot - it's just the same shared row as the parent's.
                version.getNodeSnapshot().add(originalNode);

                // Mock repository calls
                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(originalNode));
                when(nodeRepository.save(any(BaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
                when(versionRepository.save(any(Version.class))).thenReturn(version);

                // Mock NodeService to return the same instance (edited in place) -
                // editWell itself still mutates in place; the "don't mutate the shared
                // parent row" guarantee comes from editNodeInVersion cloning BEFORE
                // calling editWell, not from editWell itself.
                when(nodeService.editWell(any(Well.class), any(WellDTO.class))).thenAnswer(invocation -> {
                        Well well = invocation.getArgument(0);
                        WellDTO dto = invocation.getArgument(1);
                        well.setName(dto.getName());
                        well.setState(dto.getState());
                        return well; // Return same instance, modified
                });

                // When
                BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);

                // Then: the node came from the parent (shared row) - editNodeInVersion must
                // clone it rather than mutate originalNode, or the parent's/siblings'
                // snapshots would see the edit too.
                assertNotNull(result);
                assertNotSame(originalNode, result); // a NEW row, not the parent's shared one
                assertEquals(nodeId, result.getIdentityId()); // same conceptual entity
                assertEquals("Edited From Parent", result.getName());
                assertEquals(NodeStateEnum.REMOVED, result.getState());

                // The parent's row itself must be untouched.
                assertEquals("Original Well", originalNode.getName());
                assertEquals(NodeStateEnum.PROPOSED, originalNode.getState());

                // Verify that the snapshot now holds the clone, not the original
                assertEquals(1, version.getNodeSnapshot().size());
                assertSame(result, version.getNodeSnapshot().get(0));

                // Verify that an EDIT change was created, from the untouched parent node
                // to the new clone
                assertEquals(1, version.getNodeChanges().size());
                NodeChange nodeChange = version.getNodeChanges().get(0);
                assertEquals(ChangeTypeEnum.EDIT, nodeChange.getChangeType());
                assertSame(originalNode, nodeChange.getChangedNode());
                assertSame(result, nodeChange.getResultNode());

                // Verify that version was saved
                verify(versionRepository, times(1)).save(version);
        }

        @Test
        void editNodeInVersion_WhenCallerPassesAStaleIdForTheSameIdentity_EditsTheCurrentRowInsteadOfDuplicatingIt() {
                // Reproduces the reported bug: the client passes an id that's no longer
                // this version's current row for that identity (e.g. from before a
                // merge). editNodeInVersion must resolve the CURRENT row by identity
                // instead of trusting the id, or it ends up with two rows sharing one
                // identityId in the snapshot.
                UUID versionId = UUID.randomUUID();
                UUID identityId = UUID.randomUUID();
                UUID staleId = UUID.randomUUID(); // id the caller passes - no longer current
                WellDTO nodeDTO = new WellDTO();
                nodeDTO.setName("Edited Again");
                nodeDTO.setState(NodeStateEnum.RUNNING);

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>());

                Well staleNode = new Well(
                                "Stale Well", NodeStateEnum.PROPOSED, Instant.now(), 120,
                                MoneyAmount.of(1000000), 30, MoneyAmount.of(50000), 0.0f,
                                new InvestmentCost(), new NodeGraphData(), identityId, new NodeTypeData(),
                                100.0f, 0.5f, 0.8f, 10, MoneyAmount.of(5000), 500f);

                Well currentNode = new Well(
                                "Current Well", NodeStateEnum.PROPOSED, Instant.now(), 120,
                                MoneyAmount.of(1000000), 30, MoneyAmount.of(50000), 0.0f,
                                new InvestmentCost(), new NodeGraphData(), identityId, new NodeTypeData(),
                                100.0f, 0.5f, 0.8f, 10, MoneyAmount.of(5000), 500f);

                // Only currentNode is actually in this version's snapshot; staleNode is
                // some other row (e.g. what the parent had before) that happens to
                // share the same identity but is NOT part of this version anymore.
                version.getNodeSnapshot().add(currentNode);

                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(nodeRepository.findById(staleId)).thenReturn(Optional.of(staleNode));
                when(versionRepository.save(any(Version.class))).thenReturn(version);
                when(nodeService.editWell(any(Well.class), any(WellDTO.class))).thenAnswer(invocation -> {
                        Well well = invocation.getArgument(0);
                        WellDTO dto = invocation.getArgument(1);
                        well.setName(dto.getName());
                        well.setState(dto.getState());
                        return well;
                });

                // When
                versionService.editNodeInVersion(versionId, staleId, nodeDTO);

                // Then: still exactly one row for this identity, and it's the version's
                // own current one - not a second one spawned from the stale lookup.
                assertEquals(1, version.getNodeSnapshot().size());
                assertEquals("Edited Again", version.getNodeSnapshot().get(0).getName());
                long rowsForThisIdentity = version.getNodeSnapshot().stream()
                                .filter(n -> identityId.equals(n.getIdentityId()))
                                .count();
                assertEquals(1, rowsForThisIdentity);
        }

        @Test
        void editNodeInVersion_WhenIdentityNotInThisVersionsSnapshot_ShouldThrowIllegalArgumentException() {
                UUID versionId = UUID.randomUUID();
                UUID nodeId = UUID.randomUUID();
                WellDTO nodeDTO = new WellDTO();

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>());

                Well node = new Well(
                                "Well", NodeStateEnum.PROPOSED, Instant.now(), 120,
                                MoneyAmount.of(1000000), 30, MoneyAmount.of(50000), 0.0f,
                                new InvestmentCost(), new NodeGraphData(), UUID.randomUUID(), new NodeTypeData(),
                                100.0f, 0.5f, 0.8f, 10, MoneyAmount.of(5000), 500f);
                // version's snapshot is empty - this identity was never part of it.

                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(node));

                assertThrows(IllegalArgumentException.class,
                                () -> versionService.editNodeInVersion(versionId, nodeId, nodeDTO));
        }

        @Test
        void editNodeInVersion_WhenNodeDTOIsNull_ShouldThrowNullPointerException() {
                // Given
                UUID versionId = UUID.randomUUID();
                UUID nodeId = UUID.randomUUID();

                // When/Then
                assertThrows(NullPointerException.class,
                                () -> versionService.editNodeInVersion(versionId, nodeId, null));
        }

        @Test
        void editNodeInVersion_WhenNodeIdIsNull_ShouldThrowNullPointerException() {
                // Given
                UUID versionId = UUID.randomUUID();
                WellDTO nodeDTO = new WellDTO();

                // When/Then
                assertThrows(NullPointerException.class,
                                () -> versionService.editNodeInVersion(versionId, null, nodeDTO));
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

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>());

                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(nodeRepository.findById(nodeId)).thenReturn(Optional.empty());

                // When/Then
                assertThrows(EntityNotFoundException.class,
                                () -> versionService.editNodeInVersion(versionId, nodeId, nodeDTO));
        }

        @Test
        void addNodeToVersion_WithWellDTO_ShouldAddWellToVersion() {
                // Given
                UUID versionId = UUID.randomUUID();
                WellDTO wellDTO = new WellDTO();
                wellDTO.setName("Test Well");
                wellDTO.setState(NodeStateEnum.PROPOSED);

                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(),
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
                                new ConstantValue(0.5f), // decline_curve
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
        void mergeSubVersionIntoParent_ShouldReplaceSnapshotsAndMergeChanges() {
                // Given
                UUID parentId = UUID.randomUUID();
                UUID subId = UUID.randomUUID();

                // Create parent version with initial node
                Well parentWell = new Well(
                                "Parent Well",
                                NodeStateEnum.PROPOSED,
                                Instant.now(),
                                120,
                                MoneyAmount.of(1000000),
                                30,
                                MoneyAmount.of(50000),
                                0.0f,
                                new InvestmentCost(),
                                new NodeGraphData(),
                                UUID.randomUUID(),
                                new NodeTypeData(),
                                100.0f,
                                0.5f,
                                0.8f,
                                10,
                                MoneyAmount.of(5000),
                                500f);
                org.springframework.test.util.ReflectionTestUtils.setField(parentWell, "id", UUID.randomUUID());

                NodeChange parentAddChange = new NodeChange();
                parentAddChange.setChangeType(ChangeTypeEnum.EDIT);
                parentAddChange.setChangedNode(parentWell);
                parentAddChange.setResultNode(parentWell);

                Version parentVersion = new Version(
                                "Parent Version",
                                null,
                                new ArrayList<>(List.of(parentWell)),
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>(List.of(parentAddChange)));

                versionRepository.save(parentVersion);

                // Create subversion with modified node (EDIT)
                Well subWell = new Well(
                                "Edited Well", // Changed name
                                NodeStateEnum.RUNNING, // Changed state
                                Instant.now(),
                                120,
                                MoneyAmount.of(1000000),
                                30,
                                MoneyAmount.of(50000),
                                0.0f,
                                new InvestmentCost(),
                                new NodeGraphData(),
                                parentWell.getIdentityId(), // Same conceptual entity as parent well
                                new NodeTypeData(),
                                100.0f,
                                0.5f,
                                0.8f,
                                10,
                                MoneyAmount.of(5000),
                                500f);

                NodeChange subEditChange = new NodeChange();
                subEditChange.setChangeType(ChangeTypeEnum.EDIT);
                subEditChange.setChangedNode(parentWell); // Original state
                subEditChange.setResultNode(subWell); // Final state
                Version subVersion = new Version(
                                "Sub Version",
                                parentVersion,
                                new ArrayList<>(List.of(subWell)), // Updated snapshot
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>(List.of(subEditChange)));

                // Mock repository calls
                org.springframework.test.util.ReflectionTestUtils.setField(parentVersion, "id", parentId);
                when(versionRepository.findById(parentId)).thenReturn(Optional.of(parentVersion));
                when(versionRepository.findById(subId)).thenReturn(Optional.of(subVersion));
                when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                Version result = versionService.mergeSubVersionIntoParent(subId);

                // Then
                assertEquals("Parent Version", result.getName());
                assertEquals(1, result.getNodeSnapshot().size());
                assertTrue(result.getNodeSnapshot().contains(subWell)); // Should have subversion's snapshot

                // Should have merged change: EDIT with parent's changedNode and subversion's
                // resultNode
                assertEquals(1, result.getNodeChanges().size());
                NodeChange mergedChange = result.getNodeChanges().get(0);
                assertEquals(ChangeTypeEnum.EDIT, mergedChange.getChangeType());
                assertSame(parentWell, mergedChange.getChangedNode()); // Parent's original state
                assertSame(subWell, mergedChange.getResultNode()); // Subversion's final state

                // Conflicts with sibling versions are detected automatically as part of the merge
                verify(versionConflictService).recordMergeConflicts(result, subVersion);
        }

        @Test
        void mergeSubVersionIntoParent_AddAndDelete_ShouldCancelOut() {
                // Given
                UUID parentId = UUID.randomUUID();
                UUID subId = UUID.randomUUID();

                Well testWell = new Well(
                                "Test Well",
                                NodeStateEnum.PROPOSED,
                                Instant.now(),
                                120,
                                MoneyAmount.of(1000000),
                                30,
                                MoneyAmount.of(50000),
                                0.0f,
                                new InvestmentCost(),
                                new NodeGraphData(),
                                UUID.randomUUID(),
                                new NodeTypeData(),
                                100.0f,
                                0.5f,
                                0.8f,
                                10,
                                MoneyAmount.of(5000),
                                500f);

                // Parent: ADD the well (changedNode stays null - see addNodeToVersion;
                // ADD means nothing existed before this identity).
                NodeChange parentAddChange = new NodeChange();
                parentAddChange.setChangeType(ChangeTypeEnum.ADD);
                parentAddChange.setResultNode(testWell);

                Version parentVersion = new Version(
                                "Parent Version",
                                null,
                                new ArrayList<>(List.of(testWell)),
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>(List.of(parentAddChange)));

                versionRepository.save(parentVersion);

                // Subversion: DELETE the same well
                NodeChange subDeleteChange = new NodeChange();
                subDeleteChange.setChangeType(ChangeTypeEnum.DELETE);
                subDeleteChange.setChangedNode(testWell);
                // resultNode intentionally left null - DELETE changes never set it (see
                // deleteNodeFromVersion), and applyNodeChangesToSnapshot relies on that
                // to tell "removed" apart from "added/edited".

                Version subVersion = new Version(
                                "Sub Version",
                                parentVersion,
                                new ArrayList<>(), // Well removed from snapshot
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>(List.of(subDeleteChange)));

                // Mock repository calls
                org.springframework.test.util.ReflectionTestUtils.setField(parentVersion, "id", parentId);
                when(versionRepository.findById(parentId)).thenReturn(Optional.of(parentVersion));
                when(versionRepository.findById(subId)).thenReturn(Optional.of(subVersion));
                when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                Version result = versionService.mergeSubVersionIntoParent(subId);

                // Then
                assertEquals(0, result.getNodeSnapshot().size()); // Well should be removed
                assertEquals(0, result.getNodeChanges().size()); // ADD and DELETE should cancel out
        }

        @Test
        void mergeSubVersionIntoParent_SingleEdit_ShouldBePreserved() {
                // Given
                UUID parentId = UUID.randomUUID();
                UUID subId = UUID.randomUUID();

                Well originalWell = new Well(
                                "Original Well",
                                NodeStateEnum.PROPOSED,
                                Instant.now(),
                                120,
                                MoneyAmount.of(1000000),
                                30,
                                MoneyAmount.of(50000),
                                0.0f,
                                new InvestmentCost(),
                                new NodeGraphData(),
                                UUID.randomUUID(),
                                new NodeTypeData(),
                                100.0f,
                                0.5f,
                                0.8f,
                                10,
                                MoneyAmount.of(5000),
                                500f);
                org.springframework.test.util.ReflectionTestUtils.setField(originalWell, "id", UUID.randomUUID());

                Well editedWell = new Well(
                                "Edited Well",
                                NodeStateEnum.RUNNING,
                                Instant.now(),
                                120,
                                MoneyAmount.of(1000000),
                                30,
                                MoneyAmount.of(50000),
                                0.0f,
                                new InvestmentCost(),
                                new NodeGraphData(),
                                originalWell.getIdentityId(), // Same conceptual entity as originalWell
                                new NodeTypeData(),
                                100.0f,
                                0.5f,
                                0.8f,
                                10,
                                MoneyAmount.of(5000),
                                500f);

                // Parent: no changes
                Version parentVersion = new Version(
                                "Parent Version",
                                null,
                                new ArrayList<>(List.of(originalWell)),
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>());

                versionRepository.save(parentVersion);
                // Subversion: EDIT the well
                NodeChange subEditChange = new NodeChange();
                subEditChange.setChangeType(ChangeTypeEnum.EDIT);
                subEditChange.setChangedNode(originalWell);
                subEditChange.setResultNode(editedWell);

                Version subVersion = new Version(
                                "Sub Version",
                                parentVersion,
                                new ArrayList<>(List.of(editedWell)),
                                new ArrayList<>(),
                                new ArrayList<>(),
                                new ArrayList<>(List.of(subEditChange)));

                // Mock repository calls
                org.springframework.test.util.ReflectionTestUtils.setField(parentVersion, "id", parentId);
                when(versionRepository.findById(parentId)).thenReturn(Optional.of(parentVersion));
                when(versionRepository.findById(subId)).thenReturn(Optional.of(subVersion));
                when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

                // When
                Version result = versionService.mergeSubVersionIntoParent(subId);

                // Then
                assertEquals(1, result.getNodeSnapshot().size());
                assertTrue(result.getNodeSnapshot().contains(editedWell));

                assertEquals(1, result.getNodeChanges().size());
                NodeChange mergedChange = result.getNodeChanges().get(0);
                assertEquals(ChangeTypeEnum.EDIT, mergedChange.getChangeType());
                assertSame(originalWell, mergedChange.getChangedNode());
                assertSame(editedWell, mergedChange.getResultNode());
        }
}
