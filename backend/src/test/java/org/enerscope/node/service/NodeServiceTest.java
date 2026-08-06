package org.enerscope.node.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.*;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.repository.WellRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
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
        nodeService = new NodeService(wellRepository, null, null, null, null, null, null, null, null, null, null);
    }

    /*
     * @Test
     * void saveWellShouldReturnWell() {
     * WellDTO wellDTO = new WellDTO();
     * wellDTO.setName("Test Well");
     * wellDTO.setState(NodeStateEnum.RUNNING);
     * wellDTO.setStartupDate(Instant.parse("2025-01-01T00:00:00Z"));
     * wellDTO.setLifespanInMonths(240);
     * wellDTO.setUpkeepCosts(MoneyAmount.of("500.00"));
     * wellDTO.setMaintenanceIntervalInDays(30);
     * wellDTO.setOperatingCosts(MoneyAmount.of("200.00"));
     * wellDTO.setWastePercentage(2.5f);
     * 
     * InvestmentCostComponentDTO componentDTO = new InvestmentCostComponentDTO();
     * componentDTO.setName("Drilling");
     * componentDTO.setAmount(MoneyAmount.of("10000.00"));
     * componentDTO.setCostBasis(CostBasisEnum.FLAT);
     * 
     * InvestmentCostDTO investmentCostDTO = new InvestmentCostDTO();
     * investmentCostDTO.setComponents(List.of(componentDTO));
     * wellDTO.setInvestmentCost(investmentCostDTO);
     * 
     * NodeGraphDataDTO graphDataDTO = new NodeGraphDataDTO();
     * graphDataDTO.setXPosition(10.0);
     * graphDataDTO.setYPosition(20.0);
     * graphDataDTO.setCoordinates(0.0);
     * wellDTO.setGraphData(graphDataDTO);
     * 
     * wellDTO.setType(new NodeTypeDataDTO(VerticalEnum.EXTRACTION,
     * StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
     * 
     * wellDTO.setMaxCollectionCapacity(1000.0f);
     * wellDTO.setDeclineCurve(0.05f);
     * wellDTO.setGasRichness(0.3f);
     * wellDTO.setDTMTime(15);
     * wellDTO.setDTMCost(MoneyAmount.of("1500.00"));
     * 
     * when(wellRepository.save(any(Well.class)))
     * .thenAnswer(invocation -> invocation.getArgument(0));
     * 
     * Well result = nodeService.saveWell(wellDTO);
     * 
     * UUID savedId = result.getId();
     * when(wellRepository.findById(savedId)).thenReturn(Optional.of(result));
     * 
     * Optional<Well> foundWell = wellRepository.findById(savedId);
     * 
     * assertTrue(foundWell.isPresent());
     * assertEquals("Test Well", foundWell.get().getName());
     * assertEquals(1000.0f, foundWell.get().getMaxCollectionCapacity());
     * assertEquals(0.05f, foundWell.get().getDeclineCurve());
     * 
     * }
     */
}