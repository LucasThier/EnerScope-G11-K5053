package org.enerscope.version.service;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.IndustrialConsumptionDTO;
import org.enerscope.node.dto.InternalConsumptionDTO;
import org.enerscope.node.dto.NodeBasicsDTO;
import org.enerscope.node.dto.NodeDetailDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.repository.BaseNodeRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.node.service.NodeService;
import org.enerscope.probabilistic.UniformDistributionCase;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.enerscope.simulation.SimulationFixtures.connect;
import static org.enerscope.simulation.SimulationFixtures.consumption;
import static org.enerscope.simulation.SimulationFixtures.well;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * What the editor needed from {@link VersionService} to build models that can
 * be simulated: access checks on the four reads/writes that had none, the
 * decline curve, investment components and maintenance duration in the node
 * detail, and the two consumption node types in the add/edit paths.
 */
class VersionServiceEditorTest {

    private VersionRepository versionRepository;
    private BaseNodeRepository nodeRepository;
    private NodeService nodeService;
    private ProjectAccessGuard accessGuard;
    private VersionService service;

    private Version version;

    @BeforeEach
    void setUp() {
        versionRepository = mock(VersionRepository.class);
        nodeRepository = mock(BaseNodeRepository.class);
        nodeService = mock(NodeService.class);
        accessGuard = mock(ProjectAccessGuard.class);
        service = new VersionService(versionRepository, mock(NodeConnectionRepository.class), nodeRepository,
                mock(AppLogger.class), nodeService, mock(VersionConflictService.class), accessGuard);

        version = new Version("Base", null, new ArrayList<>(), new ArrayList<>(), null, null);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        when(versionRepository.findById(version.getId())).thenReturn(Optional.of(version));
    }

    private <T extends BaseNode> T inVersion(T node) {
        version.getNodeSnapshot().add(node);
        when(nodeRepository.findById(node.getId())).thenReturn(Optional.of(node));
        return node;
    }

    // ---- access checks ---------------------------------------------------------

    @Test
    void getDiagramRequiresAccessToTheVersion() {
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanViewVersion(version.getId());

        assertThrows(ForbiddenException.class, () -> service.getDiagram(version.getId()));
    }

    @Test
    void getDiagramAnswersNotFoundBeforeAuthorizing() {
        UUID unknown = UUID.randomUUID();
        when(versionRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(VersionNotFoundException.class, () -> service.getDiagram(unknown));
        verifyNoInteractions(accessGuard);
    }

    @Test
    void getNodeDetailRequiresAccessToTheVersion() {
        Well node = inVersion(well("Pozo", 100f));
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanViewVersion(version.getId());

        assertThrows(ForbiddenException.class, () -> service.getNodeDetail(version.getId(), node.getId()));
        verify(nodeRepository, never()).findById(any());
    }

    @Test
    void movingANodeRequiresTheEditPermission() {
        Well node = inVersion(well("Pozo", 100f));
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanEditVersion(version.getId());

        assertThrows(ForbiddenException.class,
                () -> service.updateNodePosition(version.getId(), node.getId(), new NodeGraphDataDTO()));
        verify(nodeRepository, never()).save(any());
    }

    @Test
    void renamingANodeRequiresTheEditPermission() {
        Well node = inVersion(well("Pozo", 100f));
        doThrow(new ForbiddenException("no")).when(accessGuard).assertCanEditVersion(version.getId());
        NodeBasicsDTO basics = new NodeBasicsDTO();
        basics.setName("Otro");

        assertThrows(ForbiddenException.class, () -> service.updateNodeBasics(version.getId(), node.getId(), basics));
        assertEquals("Pozo", node.getName());
    }

    // ---- node detail ------------------------------------------------------------

    @Test
    void theDetailCarriesTheDeclineCurveAsADistribution() {
        Well node = inVersion(well("Pozo", 100f));
        node.setDeclineCurve(new UniformDistributionCase(1f, 3f));

        NodeDetailDTO detail = service.getNodeDetail(version.getId(), node.getId());

        assertEquals("UNIFORM", detail.getDeclineCurve().getType());
        assertEquals(1f, detail.getDeclineCurve().getVal_a());
        assertEquals(3f, detail.getDeclineCurve().getVal_b());
        // It used to be sampled into the attributes: a different number on every read.
        assertFalse(detail.getAttributes().containsKey("declineCurve"));
    }

    @Test
    void theDetailCarriesTheInvestmentComponentsAndTheMaintenanceDuration() {
        Well node = inVersion(well("Pozo", 100f));
        node.setMaintenanceDuration(48);
        node.setInvestmentCost(new InvestmentCost(new ArrayList<>(List.of(
                new InvestmentCostComponent("Perforación", MoneyAmount.of(1500), CostBasisEnum.FLAT)))));

        NodeDetailDTO detail = service.getNodeDetail(version.getId(), node.getId());

        assertEquals(48, detail.getMaintenanceDuration());
        assertEquals(1, detail.getInvestmentCost().size());
        assertEquals("Perforación", detail.getInvestmentCost().get(0).getName());
        assertEquals(1500f, detail.getInvestmentCost().get(0).getAmount());
        assertEquals(CostBasisEnum.FLAT, detail.getInvestmentCost().get(0).getCostBasis());
    }

    @Test
    void theDetailOfAConsumptionNodeListsItsDemand() {
        InternalConsumption node = inVersion(consumption("Consumo", 5f));

        NodeDetailDTO detail = service.getNodeDetail(version.getId(), node.getId());

        assertEquals(5.0, detail.getAttributes().get("consumptionWinter"));
        assertNull(detail.getDeclineCurve());
    }

    // ---- consumption nodes ---------------------------------------------------------

    @Test
    void addNodeToVersionCreatesAnInternalConsumptionNode() {
        InternalConsumption saved = consumption("Consumo", 5f);
        when(nodeService.saveInternalConsumption(any())).thenReturn(saved);
        InternalConsumptionDTO dto = new InternalConsumptionDTO();
        dto.setMaintenanceDuration(72);

        BaseNode result = service.addNodeToVersion(version.getId(), dto);

        assertSame(saved, result);
        assertEquals(72, saved.getMaintenanceDuration(), "the duration no node constructor takes is applied after");
        assertTrue(version.getNodeSnapshot().contains(saved));
    }

    @Test
    void addNodeToVersionCreatesAnIndustrialConsumptionNode() {
        IndustrialConsumption saved = new IndustrialConsumption();
        ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
        saved.setType(new NodeTypeData(VerticalEnum.INTERNAL_CONSUMPTION, StructuralRoleEnum.SINK,
                NodeTypeEnum.INDUSTRIAL_CONSUMPTION));
        when(nodeService.saveIndustrialConsumption(any())).thenReturn(saved);

        assertSame(saved, service.addNodeToVersion(version.getId(), new IndustrialConsumptionDTO()));
    }

    @Test
    void editNodeInVersionEditsAConsumptionNodeTheVersionOwns() {
        InternalConsumption node = inVersion(consumption("Consumo", 5f));
        NodeChange added = new NodeChange();
        added.setChangeType(ChangeTypeEnum.ADD);
        added.setResultNode(node);
        version.getNodeChanges().add(added);
        when(nodeService.editInternalConsumption(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        InternalConsumptionDTO dto = new InternalConsumptionDTO();
        dto.setMaintenanceDuration(12);

        BaseNode edited = service.editNodeInVersion(version.getId(), node.getId(), dto);

        assertSame(node, edited);
        assertEquals(12, node.getMaintenanceDuration());
        verify(nodeService).editInternalConsumption(node, dto);
    }

    @Test
    void editNodeInVersionRefusesAConsumptionPayloadForAnotherNodeType() {
        Well node = inVersion(well("Pozo", 100f));
        NodeChange added = new NodeChange();
        added.setChangeType(ChangeTypeEnum.ADD);
        added.setResultNode(node);
        version.getNodeChanges().add(added);

        assertThrows(IllegalArgumentException.class,
                () -> service.editNodeInVersion(version.getId(), node.getId(), new IndustrialConsumptionDTO()));
    }

    // ---- connections follow the rows they join ------------------------------------

    /** Makes the mocked repository behave like persist: the saved instance gets an id. */
    private void saveAssignsIds() {
        when(nodeRepository.save(any())).thenAnswer(invocation -> {
            BaseNode saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                ReflectionTestUtils.setField(saved, "id", UUID.randomUUID());
            }
            return saved;
        });
    }

    @Test
    void editingAnInheritedNodeKeepsItConnected() {
        // The version holds a row it does not own yet (no change recorded for
        // it), so the edit clones it — and the clone's new id used to leave the
        // version's connection pointing at the old row: the edited well was cut
        // off and the scenario delivered nothing.
        Well well = inVersion(well("Pozo", 100f));
        InternalConsumption sink = inVersion(consumption("Consumo", 5f));
        NodeConnection link = connect(well, sink);
        version.getConnectionSnapshot().add(link);
        saveAssignsIds();
        when(nodeService.editWell(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));

        BaseNode edited = service.editNodeInVersion(version.getId(), well.getId(), new WellDTO());

        assertNotEquals(well.getId(), edited.getId(), "an inherited row is cloned, never edited in place");
        assertEquals(1, version.getConnectionSnapshot().size());
        NodeConnection current = version.getConnectionSnapshot().get(0);
        assertEquals(edited.getId(), current.getFromNodeId());
        assertEquals(sink.getId(), current.getToNodeId());
        assertEquals(link.getIdentityId(), current.getIdentityId());
        assertEquals(well.getId(), link.getFromNodeId(), "the shared row keeps its endpoints for the parent");
    }

    @Test
    void mergingRewiresTheParentsConnectionsToTheMergedRows() {
        Well parentWell = inVersion(well("Pozo", 100f));
        InternalConsumption sink = inVersion(consumption("Consumo", 5f));
        version.getConnectionSnapshot().add(connect(parentWell, sink));

        Well branchWell = well("Pozo", 300f);
        branchWell.setIdentityId(parentWell.getIdentityId());
        Version branch = new Version("Rama", version, new ArrayList<>(List.of(branchWell)), new ArrayList<>(),
                null, null);
        ReflectionTestUtils.setField(branch, "id", UUID.randomUUID());
        NodeChange edit = new NodeChange();
        edit.setChangeType(ChangeTypeEnum.EDIT);
        edit.setChangedNode(parentWell);
        edit.setResultNode(branchWell);
        branch.getNodeChanges().add(edit);
        when(versionRepository.findById(branch.getId())).thenReturn(Optional.of(branch));
        when(versionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.mergeSubVersionIntoParent(branch.getId());

        assertTrue(version.getNodeSnapshot().contains(branchWell));
        assertEquals(branchWell.getId(), version.getConnectionSnapshot().get(0).getFromNodeId());
        assertEquals(sink.getId(), version.getConnectionSnapshot().get(0).getToNodeId());
    }
}
