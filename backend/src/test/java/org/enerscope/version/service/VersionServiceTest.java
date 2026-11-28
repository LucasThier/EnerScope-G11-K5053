package org.enerscope.version.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.NodeTypeDataDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.dto.PipelineConnectionDTO;
import org.enerscope.node.dto.InternalConsumptionDTO;
import org.enerscope.node.dto.IndustrialConsumptionDTO;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

        @Mock
        private org.enerscope.scenario.service.ScenarioDefaults scenarioDefaults;
        @Mock
        private org.enerscope.economic.repository.EconomicJobRepository economicJobs;
        @Mock
        private org.enerscope.economic.repository.EconomicEvaluationRepository economicEvaluations;
        @Mock
        private org.enerscope.comparison.repository.ScenarioComparisonItemRepository comparisonItems;
        @Mock
        private org.enerscope.version.repository.VersionMemberRepository versionMembers;
        @Mock
        private org.enerscope.scenario.repository.ScenarioDraftRepository scenarioDrafts;
        @Mock
        private org.enerscope.scenario.repository.EconomicDraftRepository economicDrafts;
        @Mock
        private org.enerscope.economic.repository.EconomicConfigurationRepository economicConfigurations;

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

        @Mock
        private VersionAccessGuard accessGuard;

        @Mock
        private VersionLineage lineage;

        @InjectMocks
        private VersionService versionService;

        @Test
        void consumersCreateEditAndReloadWithinTheirVersion() throws Exception {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
            for (String type : List.of("INTERNAL_CONSUMPTION", "INDUSTRIAL_CONSUMPTION")) {
                UUID versionId = UUID.randomUUID(), nodeId = UUID.randomUUID();
                var version = new Version("Consumers", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                String json = "{\"name\":\"Consumer\",\"state\":\"RUNNING\",\"lifespanInMonths\":120,\"maintenanceIntervalInDays\":30,\"wastePercentage\":0,\"consumption\":50,\"consumptionSummer\":10,\"consumptionAutumn\":20,\"consumptionWinter\":30,\"consumptionSpring\":40,\"type\":{\"vertical\":\"INTERNAL_CONSUMPTION\",\"role\":\"SINK\",\"nodeType\":\"" + type + "\"}}";
                mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                var dto = mapper.readValue(json, org.enerscope.node.dto.BaseNodeDTO.class);
                BaseNode saved;
                if (dto instanceof org.enerscope.node.dto.InternalConsumptionDTO d) {
                    var n = new org.enerscope.node.model.export.InternalConsumption();
                    org.mockito.Mockito.doCallRealMethod().when(nodeService).editInternalConsumption(any(), any());
                    when(nodeService.saveInternalConsumption(d)).thenAnswer(inv -> { n.setIdentityId(d.getIdentity()); return nodeService.editInternalConsumption(n,d); });
                    saved = n;
                } else {
                    var d = (org.enerscope.node.dto.IndustrialConsumptionDTO) dto;
                    var n = new org.enerscope.node.model.export.IndustrialConsumption();
                    org.mockito.Mockito.doCallRealMethod().when(nodeService).editIndustrialConsumption(any(), any());
                    when(nodeService.saveIndustrialConsumption(d)).thenAnswer(inv -> { n.setIdentityId(d.getIdentity()); return nodeService.editIndustrialConsumption(n,d); });
                    saved = n;
                }
                org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", nodeId);
                assertSame(saved, versionService.addNodeToVersion(versionId, dto));
                when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(saved));
                String field = type.equals("INTERNAL_CONSUMPTION") ? "consumptionWinter" : "consumption";
                assertEquals(type.equals("INTERNAL_CONSUMPTION") ? 30d : 50d, versionService.getNodeDetail(versionId,nodeId).getAttributes().get(field));
                if (dto instanceof org.enerscope.node.dto.InternalConsumptionDTO d) d.setConsumptionWinter(12.5f);
                else ((org.enerscope.node.dto.IndustrialConsumptionDTO)dto).setConsumption(12.5f);
                versionService.editNodeInVersion(versionId,nodeId,dto);
                assertEquals(12.5d, versionService.getNodeDetail(versionId,nodeId).getAttributes().get(field));
                verify(accessGuard, org.mockito.Mockito.atLeastOnce()).assertCanEditNodeVertical(versionId, org.enerscope.node.model.enums.VerticalEnum.INTERNAL_CONSUMPTION);
                var foreign = new Version("Other", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                UUID foreignId = UUID.randomUUID(); when(versionRepository.findById(foreignId)).thenReturn(Optional.of(foreign));
                assertThrows(IllegalArgumentException.class, () -> versionService.editNodeInVersion(foreignId,nodeId,dto));
                assertThrows(IllegalArgumentException.class, () -> versionService.getNodeDetail(foreignId,nodeId));
            }
        }

        @BeforeEach
        void setUp() {
                // Using mocks for testing
                versionService = new VersionService(
                                versionRepository,
                                connectionRepository,
                                nodeRepository,
                                logger,
                                nodeService,
                                versionConflictService,
                                accessGuard,
                                lineage,
                                scenarioDefaults,
                                economicJobs,
                                economicEvaluations,
                                comparisonItems,
                                versionMembers,
                                scenarioDrafts,
                                economicDrafts,
                                economicConfigurations);
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
                                100.0f, new ConstantValue(0.5f), 0.8f, 10, MoneyAmount.of(5000), 500f);

                Well currentNode = new Well(
                                "Current Well", NodeStateEnum.PROPOSED, Instant.now(), 120,
                                MoneyAmount.of(1000000), 30, MoneyAmount.of(50000), 0.0f,
                                new InvestmentCost(), new NodeGraphData(), identityId, new NodeTypeData(),
                                100.0f, new ConstantValue(0.5f), 0.8f, 10, MoneyAmount.of(5000), 500f);

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
                                100.0f, new ConstantValue(0.5f), 0.8f, 10, MoneyAmount.of(5000), 500f);
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
                wellDTO.setType(new NodeTypeDataDTO(null, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));

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
        void addNodeToVersionSupportsPipelineConnectionAndConsumptionNodes() {
                UUID versionId = UUID.randomUUID();
                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(), new ArrayList<>());
                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(versionRepository.save(any(Version.class))).thenReturn(version);

                var pipelineDto = new PipelineConnectionDTO();
                pipelineDto.setType(new NodeTypeDataDTO(
                        VerticalEnum.TRANSPORTATION,
                        StructuralRoleEnum.INTERMEDIATE,
                        NodeTypeEnum.PIPELINE_CONECTION));


                var internalDto = new InternalConsumptionDTO();
                internalDto.setType(new NodeTypeDataDTO(
                        VerticalEnum.INTERNAL_CONSUMPTION,
                        StructuralRoleEnum.SINK,
                        NodeTypeEnum.INTERNAL_CONSUMPTION));


                var industrialDto = new IndustrialConsumptionDTO();
                industrialDto.setType(new NodeTypeDataDTO(
                        VerticalEnum.INTERNAL_CONSUMPTION,
                        StructuralRoleEnum.SINK,
                        NodeTypeEnum.INDUSTRIAL_CONSUMPTION));


                PipelineConnection pipelineConnection = mock(PipelineConnection.class);
                when(pipelineConnection.getIdentityId()).thenReturn(UUID.randomUUID());
                when(pipelineConnection.getType()).thenReturn(new NodeTypeData());
                when(nodeService.savePipelineConnection(any(PipelineConnectionDTO.class))).thenReturn(pipelineConnection);
                assertSame(pipelineConnection, versionService.addNodeToVersion(versionId, pipelineDto));

                InternalConsumption internalConsumption = mock(InternalConsumption.class);
                when(internalConsumption.getIdentityId()).thenReturn(UUID.randomUUID());
                when(internalConsumption.getType()).thenReturn(new NodeTypeData());
                when(nodeService.saveInternalConsumption(any(InternalConsumptionDTO.class))).thenReturn(internalConsumption);
                assertSame(internalConsumption, versionService.addNodeToVersion(versionId, internalDto));

                IndustrialConsumption industrialConsumption = mock(IndustrialConsumption.class);
                when(industrialConsumption.getIdentityId()).thenReturn(UUID.randomUUID());
                when(industrialConsumption.getType()).thenReturn(new NodeTypeData());
                when(nodeService.saveIndustrialConsumption(any(IndustrialConsumptionDTO.class)))
                                .thenReturn(industrialConsumption);
                assertSame(industrialConsumption, versionService.addNodeToVersion(versionId, industrialDto));

                assertEquals(3, version.getNodeSnapshot().size());
                assertEquals(3, version.getNodeChanges().size());
                verify(versionRepository, times(3)).save(version);
        }

        @Test
        void editNodeInVersionSupportsPipelineConnectionAndConsumptionNodes() {
                UUID versionId = UUID.randomUUID();
                Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(), new ArrayList<>());
                when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                when(versionRepository.save(any(Version.class))).thenReturn(version);

                PipelineConnection pipelineConnection = mock(PipelineConnection.class);
                addOwnedNode(version, pipelineConnection);
                UUID pipelineConnectionId = UUID.randomUUID();
                when(nodeRepository.findById(pipelineConnectionId)).thenReturn(Optional.of(pipelineConnection));
                PipelineConnectionDTO pipelineConnectionDTO = new PipelineConnectionDTO();
                when(nodeService.editPipelineConnection(pipelineConnection, pipelineConnectionDTO))
                                .thenReturn(pipelineConnection);
                assertSame(pipelineConnection, versionService.editNodeInVersion(
                                versionId, pipelineConnectionId, pipelineConnectionDTO));

                InternalConsumption internalConsumption = mock(InternalConsumption.class);
                addOwnedNode(version, internalConsumption);
                UUID internalConsumptionId = UUID.randomUUID();
                when(nodeRepository.findById(internalConsumptionId)).thenReturn(Optional.of(internalConsumption));
                InternalConsumptionDTO internalConsumptionDTO = new InternalConsumptionDTO();
                when(nodeService.editInternalConsumption(internalConsumption, internalConsumptionDTO))
                                .thenReturn(internalConsumption);
                assertSame(internalConsumption, versionService.editNodeInVersion(
                                versionId, internalConsumptionId, internalConsumptionDTO));

                IndustrialConsumption industrialConsumption = mock(IndustrialConsumption.class);
                addOwnedNode(version, industrialConsumption);
                UUID industrialConsumptionId = UUID.randomUUID();
                when(nodeRepository.findById(industrialConsumptionId)).thenReturn(Optional.of(industrialConsumption));
                IndustrialConsumptionDTO industrialConsumptionDTO = new IndustrialConsumptionDTO();
                when(nodeService.editIndustrialConsumption(industrialConsumption, industrialConsumptionDTO))
                                .thenReturn(industrialConsumption);
                assertSame(industrialConsumption, versionService.editNodeInVersion(
                                versionId, industrialConsumptionId, industrialConsumptionDTO));

                verify(versionRepository, times(3)).save(version);
        }

        private void addOwnedNode(Version version, BaseNode node) {
                when(node.getIdentityId()).thenReturn(UUID.randomUUID());
                version.getNodeSnapshot().add(node);
                NodeChange addChange = new NodeChange();
                addChange.setChangeType(ChangeTypeEnum.ADD);
                addChange.setResultNode(node);
                version.getNodeChanges().add(addChange);
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
                                new ConstantValue(0.5f),
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
                                new ConstantValue(0.5f),
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
                                new ConstantValue(0.5f),
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
                                new ConstantValue(0.5f),
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
                                new ConstantValue(0.5f),
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

        @Test
        void mergeSubVersionIntoParent_VersionHasNoParent_ThrowsIllegalArgumentException() {
                UUID subId = UUID.randomUUID();
                Version rootVersion = new Version("Root Version", null, new ArrayList<>(), new ArrayList<>(),
                                new ArrayList<>(), new ArrayList<>());
                when(versionRepository.findById(subId)).thenReturn(Optional.of(rootVersion));

                IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                                () -> versionService.mergeSubVersionIntoParent(subId));

                assertEquals("Version has no parent version to merge into", ex.getMessage());
                verifyNoInteractions(versionConflictService);
        }

    @Test
    void newVersionInitializesMutableEmptySnapshots() {
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));
        Version version = versionService.saveVersion(new org.enerscope.version.dto.VersionDTO("Economic example", null));
        assertNotNull(version.getNodeSnapshot());
        assertNotNull(version.getConnectionSnapshot());
        assertTrue(version.getNodeSnapshot().isEmpty());
        version.getNodeSnapshot().add(new Well());
        assertEquals(1, version.getNodeSnapshot().size());
    }

        @Test
        void nodeDetailPreservesDistributionParametersWithoutSampling() {
                var distributions = List.of(
                    org.mockito.Mockito.spy(new org.enerscope.probabilistic.ConstantValue(1.45f)),
                    org.mockito.Mockito.spy(new org.enerscope.probabilistic.NormalDistributionCase(2f, .5f)),
                    org.mockito.Mockito.spy(new org.enerscope.probabilistic.UniformDistributionCase(1f, 3f)));
                String[] types = {"CONSTANT", "NORMAL", "UNIFORM"};
                float[] first = {1.45f, 2f, 1f};
                Float[] second = {null, .5f, 3f};
                for (int index = 0; index < distributions.size(); index++) {
                    var well = new Well();
                    well.setDeclineCurve(distributions.get(index));
                    var version = new Version("Test", null, new ArrayList<>(List.of(well)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
                    var versionId = UUID.randomUUID(); var nodeId = UUID.randomUUID();
                    when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
                    when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(well));
                    for (int read = 0; read < 2; read++) {
                        var detail = versionService.getNodeDetail(versionId, nodeId);
                        assertEquals(types[index], detail.getDeclineCurve().getType());
                        assertEquals(first[index], detail.getDeclineCurve().getVal_a());
                        assertEquals(second[index], detail.getDeclineCurve().getVal_b());
                        assertTrue(!detail.getAttributes().containsKey("declineCurve"));
                    }
                    verify(distributions.get(index), org.mockito.Mockito.never()).generateValue();
                }
        }

    private Version versionWithId(UUID id) {
        Version version = new Version("V", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", id);
        return version;
    }

    @Test
    void deleteVersionAuthorizesEveryDescendantAndThenDeletesTheWholeSubtree() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID grandchildId = UUID.randomUUID();
        when(versionRepository.findById(rootId)).thenReturn(Optional.of(versionWithId(rootId)));
        when(versionRepository.findByParentVersionId(rootId)).thenReturn(List.of(versionWithId(childId)));
        when(versionRepository.findByParentVersionId(childId)).thenReturn(List.of(versionWithId(grandchildId)));
        when(versionRepository.findByParentVersionId(grandchildId)).thenReturn(List.of());

        versionService.deleteVersion(rootId);

        verify(accessGuard).assertCanManageVersion(rootId);
        verify(accessGuard).assertCanManageDescendant(rootId, childId);
        verify(accessGuard).assertCanManageDescendant(rootId, grandchildId);
        verify(versionRepository).deleteAllById(List.of(grandchildId, childId, rootId));
    }

    @Test
    void deleteVersionRemovesMembershipsAndDraftsOfTheWholeSubtreeBeforeTheVersions() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(versionRepository.findById(rootId)).thenReturn(Optional.of(versionWithId(rootId)));
        when(versionRepository.findByParentVersionId(rootId)).thenReturn(List.of(versionWithId(childId)));
        when(versionRepository.findByParentVersionId(childId)).thenReturn(List.of());

        versionService.deleteVersion(rootId);

        var order = org.mockito.Mockito.inOrder(versionMembers, scenarioDrafts, economicDrafts, economicConfigurations, versionRepository);
        order.verify(versionMembers).deleteByVersionIdIn(argThat(ids -> java.util.Set.copyOf(ids).equals(java.util.Set.of(rootId, childId))));
        order.verify(scenarioDrafts).deleteByVersionIdIn(argThat(ids -> java.util.Set.copyOf(ids).equals(java.util.Set.of(rootId, childId))));
        order.verify(economicDrafts).deleteByVersionIdIn(argThat(ids -> java.util.Set.copyOf(ids).equals(java.util.Set.of(rootId, childId))));
        order.verify(economicConfigurations).deleteByVersionIdIn(argThat(ids -> java.util.Set.copyOf(ids).equals(java.util.Set.of(rootId, childId))));
        order.verify(versionRepository).deleteAllById(List.of(childId, rootId));
    }

    @Test
    void deleteVersionKeepsMembershipsAndDraftsWhenTheSubtreeHasSavedResults() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        when(versionRepository.findById(rootId)).thenReturn(Optional.of(versionWithId(rootId)));
        when(versionRepository.findByParentVersionId(rootId)).thenReturn(List.of(versionWithId(childId)));
        when(versionRepository.findByParentVersionId(childId)).thenReturn(List.of());
        when(economicJobs.existsByVersionIdIn(List.of(rootId, childId))).thenReturn(true);

        assertThrows(org.enerscope.version.service.VersionHasResultsException.class, () -> versionService.deleteVersion(rootId));

        verify(versionMembers, org.mockito.Mockito.never()).deleteByVersionIdIn(any());
        verify(scenarioDrafts, org.mockito.Mockito.never()).deleteByVersionIdIn(any());
        verify(economicDrafts, org.mockito.Mockito.never()).deleteByVersionIdIn(any());
        verify(economicConfigurations, org.mockito.Mockito.never()).deleteByVersionIdIn(any());
        verify(versionRepository, org.mockito.Mockito.never()).deleteAllById(any());
    }

    @Test
    void deleteVersionDeletesNothingWhenAnyDescendantIsForbidden() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID grandchildId = UUID.randomUUID();
        when(versionRepository.findById(rootId)).thenReturn(Optional.of(versionWithId(rootId)));
        when(versionRepository.findByParentVersionId(rootId)).thenReturn(List.of(versionWithId(childId)));
        when(versionRepository.findByParentVersionId(childId)).thenReturn(List.of(versionWithId(grandchildId)));
        when(versionRepository.findByParentVersionId(grandchildId)).thenReturn(List.of());
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException(
                "You are not allowed to delete one or more versions below this one"))
                .when(accessGuard).assertCanManageDescendant(rootId, childId);

        org.enerscope.common.ForbiddenException ex = assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.deleteVersion(rootId));

        assertEquals("You are not allowed to delete one or more versions below this one", ex.getMessage());
        verify(versionRepository, org.mockito.Mockito.never()).deleteAllById(any());
    }

    @Test
    void deleteVersionRefusesWithoutLookingAtTheTreeWhenTheRootIsForbidden() {
        UUID rootId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("You are not allowed to manage this version"))
                .when(accessGuard).assertCanManageVersion(rootId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.deleteVersion(rootId));

        verify(versionRepository, org.mockito.Mockito.never()).findByParentVersionId(any());
        verify(versionRepository, org.mockito.Mockito.never()).deleteAllById(any());
    }

    @Test
    void deleteVersionIsTransactional() throws NoSuchMethodException {
        assertTrue(VersionService.class.getMethod("deleteVersion", UUID.class)
                .isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class));
    }

    @Test
    void getDiagramIsRefusedWhenTheCallerCannotViewTheVersion() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("You are not allowed to view this project"))
                .when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.getDiagram(versionId));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void getDiagramChecksViewAccessForAnExistingVersion() {
        UUID versionId = UUID.randomUUID();
        Version version = new Version("Shared", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        assertEquals(versionId, versionService.getDiagram(versionId).getVersionId());
        verify(accessGuard).assertCanViewVersion(versionId);
    }

    @Test
    void getDiagramOfAMissingVersionChecksAccessAndThen404sForAnAuthorizedCaller() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class, () -> versionService.getDiagram(versionId));
        verify(accessGuard).assertCanViewVersion(versionId);
    }

    @Test
    void getDiagramOfAMissingVersionAnswers403WithoutLookingItUpForAnUnauthorizedCaller() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("You are not allowed to access this version"))
                .when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.getDiagram(versionId));
        verify(versionRepository, org.mockito.Mockito.never()).findById(any());
    }

    @Test
    void getNodeDetailIsRefusedBeforeLoadingTheNodeWhenTheCallerCannotViewTheVersion() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("You are not allowed to view this project"))
                .when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.getNodeDetail(versionId, nodeId));
        verifyNoInteractions(versionRepository);
        verifyNoInteractions(nodeRepository);
    }

    private static Well wellTyped(UUID id, VerticalEnum storedVertical) {
        Well well = new Well("Typed Well", NodeStateEnum.PROPOSED, Instant.now(), 120, MoneyAmount.of(1), 30,
                MoneyAmount.of(1), 0.0f, new InvestmentCost(), new NodeGraphData(), UUID.randomUUID(),
                new NodeTypeData(storedVertical, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL),
                1f, new ConstantValue(0.5f), 0.5f, 1, MoneyAmount.of(1), 1f);
        org.springframework.test.util.ReflectionTestUtils.setField(well, "id", id);
        return well;
    }

    private static WellDTO wellDeclaredAs(VerticalEnum vertical) {
        WellDTO dto = new WellDTO();
        dto.setName("Declared Well");
        dto.setType(new NodeTypeDataDTO(vertical, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
        return dto;
    }

    @Test
    void addNodeToVersionAuthorizesWithTheVerticalDerivedFromTheTypeBeforeCheckingTheDeclaredOne() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("denied"))
                .when(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.addNodeToVersion(versionId, wellDeclaredAs(VerticalEnum.EXPORT)));
        verify(accessGuard, org.mockito.Mockito.never()).assertCanEditNodeVertical(versionId, VerticalEnum.EXPORT);
        verifyNoInteractions(nodeService, versionRepository);
    }

    @Test
    void addNodeToVersionRejectsDeclaredVerticalThatDoesNotMatchTheType() {
        UUID versionId = UUID.randomUUID();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> versionService.addNodeToVersion(versionId, wellDeclaredAs(VerticalEnum.INTERNAL_CONSUMPTION)));

        assertEquals("Node vertical does not match its type: WELL belongs to EXTRACTION, not INTERNAL_CONSUMPTION",
                error.getMessage());
        verify(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);
        verifyNoInteractions(nodeService, versionRepository);
    }

    @Test
    void addNodeToVersionFillsAMissingVerticalFromTheType() {
        UUID versionId = UUID.randomUUID();
        Version version = new Version("V", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeService.saveWell(any(WellDTO.class))).thenReturn(wellTyped(UUID.randomUUID(), VerticalEnum.EXTRACTION));
        WellDTO dto = wellDeclaredAs(null);

        versionService.addNodeToVersion(versionId, dto);

        assertEquals(VerticalEnum.EXTRACTION, dto.getType().getVertical());
        verify(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);
    }

    @Test
    void addNodeToVersionRejectsMissingNodeType() {
        UUID versionId = UUID.randomUUID();
        WellDTO withoutType = new WellDTO();
        WellDTO withoutNodeType = wellDeclaredAs(VerticalEnum.EXTRACTION);
        withoutNodeType.getType().setNodeType(null);

        assertThrows(IllegalArgumentException.class, () -> versionService.addNodeToVersion(versionId, withoutType));
        assertThrows(IllegalArgumentException.class, () -> versionService.addNodeToVersion(versionId, withoutNodeType));
        verifyNoInteractions(accessGuard, nodeService, versionRepository);
    }

    @Test
    void editNodeInVersionRejectsChangingTheVerticalOfTheNode() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Well stored = wellTyped(nodeId, VerticalEnum.EXTRACTION);
        Version version = new Version("V", null, new ArrayList<>(List.of(stored)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(stored));

        assertThrows(IllegalArgumentException.class,
                () -> versionService.editNodeInVersion(versionId, nodeId, wellDeclaredAs(VerticalEnum.EXPORT)));

        verify(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);
        verifyNoInteractions(nodeService);
        assertEquals(VerticalEnum.EXTRACTION, stored.getType().getVertical());
        assertSame(stored, version.getNodeSnapshot().get(0));
    }

    @Test
    void editNodeInVersionAuthorizesByTheStoredNodeTypeNotTheStoredVertical() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Well spoofed = wellTyped(nodeId, VerticalEnum.EXPORT);
        Version version = new Version("V", null, new ArrayList<>(List.of(spoofed)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(spoofed));
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("denied"))
                .when(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.editNodeInVersion(versionId, nodeId, wellDeclaredAs(VerticalEnum.EXTRACTION)));
        verify(accessGuard, org.mockito.Mockito.never()).assertCanEditNodeVertical(versionId, VerticalEnum.EXPORT);
        verifyNoInteractions(nodeService);
    }

    @Test
    void deleteNodeFromVersionAuthorizesByTheStoredNodeType() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Well spoofed = wellTyped(nodeId, VerticalEnum.EXPORT);
        Version version = new Version("V", null, new ArrayList<>(List.of(spoofed)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(spoofed));
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("denied"))
                .when(accessGuard).assertCanEditNodeVertical(versionId, VerticalEnum.EXTRACTION);

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.deleteNodeFromVersion(versionId, nodeId));
        assertTrue(version.getNodeSnapshot().contains(spoofed));
    }

    @Test
    void diagramAndNodeDetailReportTheVerticalDerivedFromTheType() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Well spoofed = wellTyped(nodeId, VerticalEnum.EXPORT);
        Version version = new Version("V", null, new ArrayList<>(List.of(spoofed)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(spoofed));

        assertEquals(VerticalEnum.EXTRACTION,
                versionService.getDiagram(versionId).getNodes().get(0).getType().getVertical());
        assertEquals(VerticalEnum.EXTRACTION,
                versionService.getNodeDetail(versionId, nodeId).getType().getVertical());
    }

    @Test
    void nodeDetailReportsTheStartupDateAndMaintenanceDurationSoTheEditFormCanSendThemBack() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        Well well = wellTyped(nodeId, VerticalEnum.EXTRACTION);
        well.setStartupDate(java.time.Instant.parse("2030-01-01T00:00:00Z"));
        well.setMaintenanceDuration(7);
        Version version = new Version("V", null, new ArrayList<>(List.of(well)), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(nodeRepository.findById(nodeId)).thenReturn(Optional.of(well));

        var detail = versionService.getNodeDetail(versionId, nodeId);

        assertEquals(java.time.Instant.parse("2030-01-01T00:00:00Z"), detail.getStartupDate());
        assertEquals(7, detail.getMaintenanceDuration());
    }

    @Test
    void mergeAuthorizesTheDestinationAndEveryTouchedNodeInsteadOfTheSubversion() {
        Version parent = versionWithId("Parent", null);
        Well original = typedWell(NodeTypeEnum.WELL, VerticalEnum.EXTRACTION);
        parent.getNodeSnapshot().add(original);
        Version sub = versionWithId("Sub", parent);
        Well edited = typedWell(NodeTypeEnum.WELL, VerticalEnum.EXTRACTION);
        edited.setIdentityId(original.getIdentityId());
        Well added = typedWell(NodeTypeEnum.LNG_CAMER, VerticalEnum.EXTRACTION);
        sub.getNodeChanges().add(nodeChange(ChangeTypeEnum.EDIT, original, edited));
        sub.getNodeChanges().add(nodeChange(ChangeTypeEnum.ADD, null, added));
        stubMerge(parent, sub);

        versionService.mergeSubVersionIntoParent(sub.getId());

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accessGuard, lineage);
        order.verify(accessGuard).assertCanViewVersion(sub.getId());
        order.verify(accessGuard).assertCanManageVersion(parent.getId());
        order.verify(accessGuard).assertCanEditNodes(parent.getId(), List.of(original, edited, added));
        order.verify(lineage).assertSameProject(sub.getId(), parent.getId());
        verify(accessGuard, org.mockito.Mockito.never()).assertCanManageVersion(sub.getId());
        verify(accessGuard, org.mockito.Mockito.never()).assertCanEditConnection(any());
    }

    @Test
    void mergeRefusedForOneVerticalLeavesTheParentUntouched() {
        Version parent = versionWithId("Parent", null);
        Well original = typedWell(NodeTypeEnum.WELL, VerticalEnum.EXTRACTION);
        parent.getNodeSnapshot().add(original);
        Version sub = versionWithId("Sub", parent);
        sub.getNodeChanges().add(nodeChange(ChangeTypeEnum.DELETE, original, null));
        stubMerge(parent, sub);
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("denied"))
                .when(accessGuard).assertCanEditNodes(org.mockito.ArgumentMatchers.eq(parent.getId()), any());

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.mergeSubVersionIntoParent(sub.getId()));

        assertEquals(List.of(original), parent.getNodeSnapshot());
        assertTrue(parent.getNodeChanges().isEmpty());
        verify(versionRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(versionConflictService, lineage);
    }

    @Test
    void mergeOfAConnectionChangeRequiresEditingConnectionsOnTheDestination() {
        Version parent = versionWithId("Parent", null);
        org.enerscope.node.model.NodeConnection connection = new org.enerscope.node.model.NodeConnection(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        parent.getConnectionSnapshot().add(connection);
        Version sub = versionWithId("Sub", parent);
        org.enerscope.node.model.ConnectionChange change = new org.enerscope.node.model.ConnectionChange();
        change.setChangeType(ChangeTypeEnum.DELETE);
        change.setChangedConnection(connection);
        sub.getConnectionChanges().add(change);
        stubMerge(parent, sub);
        org.mockito.Mockito.doThrow(new org.enerscope.common.ForbiddenException("denied"))
                .when(accessGuard).assertCanEditConnection(parent.getId());

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.mergeSubVersionIntoParent(sub.getId()));

        assertEquals(List.of(connection), parent.getConnectionSnapshot());
        verify(versionRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(versionConflictService);
    }

    @Test
    void mergeBetweenProjectsIsRejectedAfterAuthorizingAndChangesNothing() {
        Version parent = versionWithId("Parent", null);
        Well original = typedWell(NodeTypeEnum.WELL, VerticalEnum.EXTRACTION);
        parent.getNodeSnapshot().add(original);
        Version sub = versionWithId("Sub", parent);
        sub.getNodeChanges().add(nodeChange(ChangeTypeEnum.DELETE, original, null));
        stubMerge(parent, sub);
        org.mockito.Mockito.doThrow(new IllegalArgumentException("The versions belong to different projects"))
                .when(lineage).assertSameProject(sub.getId(), parent.getId());

        assertThrows(IllegalArgumentException.class, () -> versionService.mergeSubVersionIntoParent(sub.getId()));

        verify(accessGuard).assertCanManageVersion(parent.getId());
        assertEquals(List.of(original), parent.getNodeSnapshot());
        verify(versionRepository, org.mockito.Mockito.never()).save(any());
        verifyNoInteractions(versionConflictService);
    }

    @Test
    void reparentingRequiresManagingTheVersionAndEditingTheNewParentThenChecksTheLineage() {
        Version version = versionWithId("Moved", null);
        Version newParent = versionWithId("New parent", null);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(versionRepository.findById(newParent.getId())).thenReturn(Optional.of(newParent));
        when(versionRepository.save(version)).thenReturn(version);

        versionService.modifyVersion(version.getId(), new org.enerscope.version.dto.VersionDTO(null, newParent.getId()));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(accessGuard, lineage);
        order.verify(accessGuard).assertCanEditVersion(version.getId());
        order.verify(accessGuard).assertCanManageVersion(version.getId());
        order.verify(accessGuard).assertCanEditVersion(newParent.getId());
        order.verify(lineage).assertParentInSameProject(version.getId(), newParent.getId());
        order.verify(lineage).assertNotAncestor(version.getId(), newParent);
        assertSame(newParent, version.getParentVersion());
    }

    @Test
    void reparentingRejectedByTheLineageKeepsTheOldParent() {
        Version oldParent = versionWithId("Old parent", null);
        Version version = versionWithId("Moved", oldParent);
        Version foreign = versionWithId("Foreign", null);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(versionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));
        org.mockito.Mockito.doThrow(new IllegalArgumentException("Parent version must belong to the same project"))
                .when(lineage).assertParentInSameProject(version.getId(), foreign.getId());

        assertThrows(IllegalArgumentException.class, () -> versionService.modifyVersion(version.getId(),
                new org.enerscope.version.dto.VersionDTO(null, foreign.getId())));

        assertSame(oldParent, version.getParentVersion());
        verify(versionRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void renamingDoesNotRequireManagingTheVersion() {
        Version version = versionWithId("Old", null);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(versionRepository.save(version)).thenReturn(version);

        versionService.modifyVersion(version.getId(), new org.enerscope.version.dto.VersionDTO("New", null));

        verify(accessGuard).assertCanEditVersion(version.getId());
        verify(accessGuard, org.mockito.Mockito.never()).assertCanManageVersion(any());
        verifyNoInteractions(lineage);
    }

    @Test
    void branchingStoresTheVerticalDerivedFromTheTypeNotTheStoredOne() {
        Version parent = versionWithId("Parent", null);
        parent.getNodeSnapshot().add(typedWell(NodeTypeEnum.WELL, VerticalEnum.EXPORT));
        when(versionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        when(nodeRepository.save(any(BaseNode.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Version child = versionService.saveVersion(new org.enerscope.version.dto.VersionDTO("Child", parent.getId()));

        assertEquals(VerticalEnum.EXTRACTION, child.getNodeSnapshot().get(0).getType().getVertical());
        assertEquals(NodeTypeEnum.WELL, child.getNodeSnapshot().get(0).getType().getNodeType());
    }

    private static org.enerscope.common.ForbiddenException denied() {
        return new org.enerscope.common.ForbiddenException("denied");
    }

    @Test
    void saveOrphanVersionIsRefusedForANonPlatformAdminAndSavesNothing() {
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertIsPlatformAdmin("create detached versions");

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.saveOrphanVersion(new org.enerscope.version.dto.VersionDTO("Detached", null)));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveOrphanVersionCreatesTheVersionForAPlatformAdmin() {
        when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Version created = versionService.saveOrphanVersion(new org.enerscope.version.dto.VersionDTO("Detached", null));

        assertEquals("Detached", created.getName());
        verify(accessGuard).assertIsPlatformAdmin("create detached versions");
    }

    @Test
    void getVersionIsRefusedWithoutLookingItUpWhenTheCallerCannotViewIt() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.getVersion(versionId));
        verifyNoInteractions(versionRepository);
    }

    @Test
    void getVersionChecksViewAccessAndReturnsTheVersion() {
        Version version = versionWithId("Shared", null);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));

        assertSame(version, versionService.getVersion(version.getId()));
        verify(accessGuard).assertCanViewVersion(version.getId());
    }

    @Test
    void getVersionOfAMissingVersionChecksAccessAndThen404sForAnAuthorizedCaller() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class, () -> versionService.getVersion(versionId));
        verify(accessGuard).assertCanViewVersion(versionId);
    }

    @Test
    void updateNodePositionIsRefusedBeforeLoadingAnythingWhenTheCallerCannotViewTheVersion() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.updateNodePosition(
                versionId, UUID.randomUUID(), new org.enerscope.node.dto.NodeGraphDataDTO()));
        verifyNoInteractions(versionRepository, nodeRepository);
    }

    @Test
    void updateNodePositionIsRefusedWhenTheCallerCannotEditTheVerticalOfTheNodeAndSavesNothing() {
        Version version = versionWithId("V", null);
        Well node = wellTyped(UUID.randomUUID(), VerticalEnum.EXTRACTION);
        version.getNodeSnapshot().add(node);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        org.mockito.Mockito.doThrow(denied()).when(accessGuard)
                .assertCanEditNodeVertical(version.getId(), VerticalEnum.EXTRACTION);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.updateNodePosition(
                version.getId(), node.getId(), new org.enerscope.node.dto.NodeGraphDataDTO()));
        verify(nodeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateNodePositionOfAnUntypedNodeFallsBackToTheVersionEditCheck() {
        Version version = versionWithId("V", null);
        Well node = new Well();
        org.springframework.test.util.ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanEditVersion(version.getId());

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.updateNodePosition(
                version.getId(), node.getId(), new org.enerscope.node.dto.NodeGraphDataDTO()));
        verify(accessGuard, org.mockito.Mockito.never()).assertCanEditNodeVertical(any(), any());
        verify(nodeRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateNodeBasicsIsRefusedBeforeLoadingAnythingWhenTheCallerCannotViewTheVersion() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanViewVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.updateNodeBasics(
                versionId, UUID.randomUUID(), new org.enerscope.node.dto.NodeBasicsDTO()));
        verifyNoInteractions(versionRepository, nodeRepository);
    }

    @Test
    void updateNodeBasicsIsRefusedWhenTheCallerCannotEditTheVerticalOfTheNodeAndSavesNothing() {
        Version version = versionWithId("V", null);
        Well node = wellTyped(UUID.randomUUID(), VerticalEnum.EXTRACTION);
        version.getNodeSnapshot().add(node);
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        org.mockito.Mockito.doThrow(denied()).when(accessGuard)
                .assertCanEditNodeVertical(version.getId(), VerticalEnum.EXTRACTION);
        org.enerscope.node.dto.NodeBasicsDTO basics = new org.enerscope.node.dto.NodeBasicsDTO();
        basics.setName("Renamed");

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.updateNodeBasics(version.getId(), node.getId(), basics));
        assertEquals("Typed Well", node.getName());
        verify(nodeRepository, org.mockito.Mockito.never()).save(any());
    }

    private static org.enerscope.node.dto.ConnectionDTO connectionDto() {
        org.enerscope.node.dto.ConnectionDTO dto = new org.enerscope.node.dto.ConnectionDTO();
        dto.setFromNodeId(UUID.randomUUID());
        dto.setToNodeId(UUID.randomUUID());
        return dto;
    }

    @Test
    void addConnectionToVersionIsRefusedBeforeTouchingAnythingWhenTheCallerCannotEditConnections() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanEditConnection(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.addConnectionToVersion(versionId, connectionDto()));
        verifyNoInteractions(versionRepository, nodeRepository, nodeService);
    }

    @Test
    void addConnectionToVersionChecksConnectionEditAccessAndThen404sForAMissingVersion() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class,
                () -> versionService.addConnectionToVersion(versionId, connectionDto()));
        verify(accessGuard).assertCanEditConnection(versionId);
    }

    @Test
    void addConnectionToVersionRefusesAnIdentityTheVersionAlreadyHasWithoutSaving() {
        UUID versionId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        org.enerscope.node.model.NodeConnection existing = new org.enerscope.node.model.NodeConnection(
                identity, UUID.randomUUID(), UUID.randomUUID());
        Version version = new Version("v", null, new java.util.ArrayList<>(),
                new java.util.ArrayList<>(java.util.List.of(existing)), new java.util.ArrayList<>(),
                new java.util.ArrayList<>(), new java.util.ArrayList<>());
        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        org.enerscope.node.dto.ConnectionDTO dto = connectionDto();
        dto.setIdentity(identity);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> versionService.addConnectionToVersion(versionId, dto));

        org.junit.jupiter.api.Assertions.assertEquals(
                "Connection identity " + identity + " already exists in this version", error.getMessage());
        org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of(existing), version.getConnectionSnapshot());
        org.junit.jupiter.api.Assertions.assertTrue(version.getConnectionChanges().isEmpty());
        verifyNoInteractions(nodeService);
        org.mockito.Mockito.verify(versionRepository, org.mockito.Mockito.never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void editConnectionInVersionIsRefusedBeforeTouchingAnythingWhenTheCallerCannotEditConnections() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanEditConnection(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.editConnectionInVersion(
                versionId, UUID.randomUUID(), connectionDto()));
        verifyNoInteractions(versionRepository, connectionRepository, nodeService);
    }

    @Test
    void editConnectionInVersionChecksConnectionEditAccessAndThen404sForAMissingVersion() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class, () -> versionService.editConnectionInVersion(
                versionId, UUID.randomUUID(), connectionDto()));
        verify(accessGuard).assertCanEditConnection(versionId);
    }

    @Test
    void deleteConnectionFromVersionIsRefusedBeforeTouchingAnythingWhenTheCallerCannotEditConnections() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanEditConnection(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class,
                () -> versionService.deleteConnectionFromVersion(versionId, UUID.randomUUID()));
        verifyNoInteractions(versionRepository, connectionRepository);
    }

    @Test
    void deleteConnectionFromVersionChecksConnectionEditAccessAndThen404sForAMissingVersion() {
        UUID versionId = UUID.randomUUID();
        when(versionRepository.findById(versionId)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class,
                () -> versionService.deleteConnectionFromVersion(versionId, UUID.randomUUID()));
        verify(accessGuard).assertCanEditConnection(versionId);
    }

    @Test
    void modifyVersionIsRefusedBeforeLoadingTheVersionWhenTheCallerCannotEditIt() {
        UUID versionId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(denied()).when(accessGuard).assertCanEditVersion(versionId);

        assertThrows(org.enerscope.common.ForbiddenException.class, () -> versionService.modifyVersion(
                versionId, new org.enerscope.version.dto.VersionDTO("New", null)));
        verifyNoInteractions(versionRepository, lineage);
    }

    @Test
    void creatingARootVersionAndABranchEachCreateTheirDefaultScenarioInTheSameCall() {
        when(versionRepository.save(any(Version.class))).thenAnswer(call -> call.getArgument(0));
        Version parent = versionService.saveVersion(new org.enerscope.version.dto.VersionDTO("Raíz", null));
        when(versionRepository.findById(any())).thenReturn(Optional.of(parent));

        Version child = versionService.saveVersion(new org.enerscope.version.dto.VersionDTO("Hija", UUID.randomUUID()));

        org.mockito.Mockito.verify(scenarioDefaults).forVersion(parent);
        org.mockito.Mockito.verify(scenarioDefaults).forVersion(child);
        org.mockito.Mockito.verifyNoMoreInteractions(scenarioDefaults);
    }

    @Test
    void saveVersionRejectsABlankName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> versionService.saveVersion(new org.enerscope.version.dto.VersionDTO("", null)));

        assertEquals("Version name cannot be blank or whitespace only", error.getMessage());
        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveVersionRejectsAWhitespaceOnlyName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> versionService.saveVersion(new org.enerscope.version.dto.VersionDTO(" \t ", null)));

        assertEquals("Version name cannot be blank or whitespace only", error.getMessage());
        verifyNoInteractions(versionRepository);
    }

    @Test
    void saveVersionRejectsANullName() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> versionService.saveVersion(new org.enerscope.version.dto.VersionDTO(null, null)));

        assertEquals("Version name cannot be null", error.getMessage());
        verifyNoInteractions(versionRepository);
    }

    private void stubMerge(Version parent, Version sub) {
        when(versionRepository.findById(sub.getId())).thenReturn(Optional.of(sub));
        when(versionRepository.findById(parent.getId())).thenReturn(Optional.of(parent));
        org.mockito.Mockito.lenient().when(versionRepository.save(any(Version.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static Version versionWithId(String name, Version parent) {
        Version version = new Version(name, parent, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>());
        org.springframework.test.util.ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }

    private static Well typedWell(NodeTypeEnum type, VerticalEnum storedVertical) {
        Well well = new Well();
        well.setName("Well " + type);
        well.setIdentityId(UUID.randomUUID());
        well.setType(new NodeTypeData(storedVertical, StructuralRoleEnum.GENERATOR, type));
        org.springframework.test.util.ReflectionTestUtils.setField(well, "id", UUID.randomUUID());
        return well;
    }

    private static NodeChange nodeChange(ChangeTypeEnum type, BaseNode changed, BaseNode result) {
        NodeChange change = new NodeChange();
        change.setChangeType(type);
        change.setChangedNode(changed);
        change.setResultNode(result);
        return change;
    }
}
