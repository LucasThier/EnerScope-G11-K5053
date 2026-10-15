package org.enerscope.node.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.*;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.probabilistic.UniformDistributionCase;
import org.enerscope.node.repository.WellRepository;
import org.enerscope.probabilistic.ConstantValue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class NodeServiceTest {

    @Mock
    private WellDTO mockWellDTO;

    @Mock
    private PipelineDTO mockPipelineDTO;

    @Mock
    private GatheringNetworkDTO mockGnDTO;

    @Mock
    private TreatmentPlantDTO mockTpDTO;

    @Mock
    private CompressingPlantDTO mockCpDTO;

    @Mock
    private GroundBasedLiquefactionPlantDTO mockGblpDTO;

    @Mock
    private FLNGUnitDTO mockFlngDTO;

    @Mock
    private LNGCarrierDTO mockLngDTO;

    @Mock
    private SeaportTerminalDTO mockStDTO;

    @Mock
    private PipelineConnectionDTO mockPcDTO;

    @Mock
    private ConnectionDTO mockConnDTO;

    @Mock
    private WellRepository wellRepository;

    @InjectMocks
    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        // Using a mock or stub implementation for testing
        nodeService = new NodeService(wellRepository, null, null, null, null, null,
                null, null, null, null, null, null,null);
    }

    @Test
    void saveWellShouldReturnWell() {
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        wellDTO.setState(NodeStateEnum.RUNNING);
        wellDTO.setStartupDate(Instant.parse("2025-01-01T00:00:00Z"));
        wellDTO.setLifespanInMonths(240);
        wellDTO.setUpkeepCosts(500.00f);
        wellDTO.setMaintenanceIntervalInDays(30);
        wellDTO.setOperatingCosts(200.00f);
        wellDTO.setWastePercentage(2.5f);

        InvestmentCostComponentDTO componentDTO = new InvestmentCostComponentDTO();
        componentDTO.setName("Drilling");
        componentDTO.setAmount(1000.0f);
        componentDTO.setCostBasis(CostBasisEnum.FLAT);

        InvestmentCostDTO investmentCostDTO = new InvestmentCostDTO();
        investmentCostDTO.setComponents(List.of(componentDTO));
        wellDTO.setInvestmentCost(investmentCostDTO);

        NodeGraphDataDTO graphDataDTO = new NodeGraphDataDTO(
                new GraphPositionDTO(10.0, 20.0),
                new GeographicalPositionDTO(-70.0, -34.0));
        wellDTO.setGraphData(graphDataDTO);

        wellDTO.setType(new NodeTypeDataDTO(VerticalEnum.EXTRACTION,
                StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));

        wellDTO.setMaxCollectionCapacity(1000.0f);
        wellDTO.setDeclineCurve(new ProbabilisticDistributionDTO("CONSTANT",0.05f,null));
        wellDTO.setGasRichness(0.3f);
        wellDTO.setDTMTime(15);
        wellDTO.setDTMCost(150.000f);
        wellDTO.setSurface(10.0f);

        when(wellRepository.save(any(Well.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Well result = nodeService.saveWell(wellDTO);

        UUID savedId = result.getId();
        when(wellRepository.findById(savedId)).thenReturn(Optional.of(result));

        Optional<Well> foundWell = wellRepository.findById(savedId);

        assertTrue(foundWell.isPresent());
        assertEquals("Test Well", foundWell.get().getName());
        assertEquals(1000.0f, foundWell.get().getMaxCollectionCapacity());
        assertEquals(0.05f, foundWell.get().getDeclineCurve().generateValue());
    }

    @Test
    void editWellShouldNeverChangeIdentityEvenIfDtoCarriesADifferentOne() {
        // Reproduces the reported bug: identityId is the stable key version
        // merges/conflicts match nodes by across edits - editWell must ignore
        // whatever the DTO's identity field carries (e.g. a stale/placeholder
        // value from a client form), not silently reassign it.
        UUID originalIdentity = UUID.randomUUID();
        Well well = new Well("Original", NodeStateEnum.PROPOSED, Instant.now(), 120,
                MoneyAmount.of(1000), 30, MoneyAmount.of(500),
                0.1f, null, null, originalIdentity, null,
                10f, new ConstantValue(0.5f), 0.5f, 5, MoneyAmount.of(50), 100f);

        WellDTO editDTO = new WellDTO();
        editDTO.setName("Edited");
        editDTO.setState(NodeStateEnum.RUNNING);
        editDTO.setLifespanInMonths(120);
        editDTO.setMaintenanceIntervalInDays(30);
        editDTO.setWastePercentage(0.1f);
        editDTO.setMaxCollectionCapacity(10f);
        editDTO.setDeclineCurve(new ProbabilisticDistributionDTO("CONSTANT",0.5f,null));
        editDTO.setGasRichness(0.5f);
        editDTO.setDTMTime(5);
        editDTO.setSurface(100f);
        editDTO.setIdentity(UUID.randomUUID()); // a different identity - must be ignored

        Well edited = nodeService.editWell(well, editDTO);

        assertEquals("Edited", edited.getName());
        assertEquals(originalIdentity, edited.getIdentityId());
    }


    /** A minimal valid well payload; the decline curve is set per test. */
    private static WellDTO wellDTO(ProbabilisticDistributionDTO declineCurve) {
        WellDTO dto = new WellDTO();
        dto.setName("Pozo");
        dto.setState(NodeStateEnum.PROPOSED);
        dto.setLifespanInMonths(240);
        dto.setUpkeepCosts(0f);
        dto.setMaintenanceIntervalInDays(365);
        dto.setOperatingCosts(0f);
        dto.setWastePercentage(0f);
        InvestmentCostDTO investment = new InvestmentCostDTO();
        investment.setComponents(List.of());
        dto.setInvestmentCost(investment);
        dto.setType(new NodeTypeDataDTO(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
        dto.setMaxCollectionCapacity(100f);
        dto.setDeclineCurve(declineCurve);
        dto.setGasRichness(0.5f);
        dto.setDTMCost(0f);
        dto.setSurface(1f);
        return dto;
    }

    @Test
    void saveWellWithoutADeclineCurveStoresNone() {
        when(wellRepository.save(any(Well.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Used to throw a NullPointerException reading the missing curve's type.
        assertNull(nodeService.saveWell(wellDTO(null)).getDeclineCurve());
    }

    @Test
    void saveWellReadsTheDistributionTypeWhateverItsCase() {
        when(wellRepository.save(any(Well.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Well well = nodeService.saveWell(wellDTO(new ProbabilisticDistributionDTO("uniform", 1f, 3f)));

        UniformDistributionCase curve = assertInstanceOf(UniformDistributionCase.class, well.getDeclineCurve());
        assertEquals(1f, curve.firstParameter());
        assertEquals(3f, curve.secondParameter());
    }

    @Test
    void saveWellRejectsATwoParameterDistributionGivenOnlyOne() {
        assertThrows(IllegalArgumentException.class,
                () -> nodeService.saveWell(wellDTO(new ProbabilisticDistributionDTO("NORMAL", 5f, null))));
    }

    @Test
    void saveWellRejectsADistributionWithoutAType() {
        assertThrows(IllegalArgumentException.class,
                () -> nodeService.saveWell(wellDTO(new ProbabilisticDistributionDTO(null, 5f, null))));
    }

    @Test
    void editWellUpdatesTheSurface() {
        Well well = new Well("Original", NodeStateEnum.PROPOSED, Instant.now(), 120,
                MoneyAmount.of(1000), 30, MoneyAmount.of(500),
                0.1f, null, null, UUID.randomUUID(), null,
                10f, new ConstantValue(0.5f), 0.5f, 5, MoneyAmount.of(50), 100f);
        WellDTO edit = wellDTO(null);
        edit.setSurface(42f);

        // Every other well field was copied on edit; the surface was left behind,
        // so a PER_KM2 investment kept pricing the old area.
        assertEquals(42f, nodeService.editWell(well, edit).getSurface());
    }

    @Test
    void editingAConsumptionNodeNeverChangesItsIdentity() {
        UUID internalIdentity = UUID.randomUUID();
        UUID industrialIdentity = UUID.randomUUID();
        InternalConsumption internal = new InternalConsumption();
        internal.setIdentityId(internalIdentity);
        IndustrialConsumption industrial = new IndustrialConsumption();
        industrial.setIdentityId(industrialIdentity);

        InternalConsumptionDTO internalEdit = new InternalConsumptionDTO();
        internalEdit.setName("Consumo");
        internalEdit.setLifespanInMonths(240);
        internalEdit.setMaintenanceIntervalInDays(365);
        internalEdit.setWastePercentage(0f);
        internalEdit.setConsumptionSummer(1f);
        internalEdit.setConsumptionAutumn(2f);
        internalEdit.setConsumptionWinter(9f);
        internalEdit.setConsumptionSpring(4f);
        internalEdit.setIdentity(UUID.randomUUID());
        IndustrialConsumptionDTO industrialEdit = new IndustrialConsumptionDTO();
        industrialEdit.setName("Industria");
        industrialEdit.setLifespanInMonths(240);
        industrialEdit.setMaintenanceIntervalInDays(365);
        industrialEdit.setWastePercentage(0f);
        industrialEdit.setConsumption(7f);
        industrialEdit.setIdentity(UUID.randomUUID());

        assertEquals(internalIdentity, nodeService.editInternalConsumption(internal, internalEdit).getIdentityId());
        assertEquals(9f, internal.getConsumptionWinter());
        assertEquals(industrialIdentity, nodeService.editIndustrialConsumption(industrial, industrialEdit).getIdentityId());
        assertEquals(7f, industrial.getConsumption());
    }

}
