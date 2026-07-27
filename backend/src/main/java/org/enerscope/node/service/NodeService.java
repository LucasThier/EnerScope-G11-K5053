package org.enerscope.node.service;

import org.enerscope.node.dto.*;
import org.enerscope.node.model.ConnectionIdentity;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeIdentity;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.node.model.extraction.ExtractionNode;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.liquefaction.LiquefactionNode;
import org.enerscope.node.model.export.ExportNode;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.model.transportation.TransportNode;
import org.enerscope.node.repository.CompressingPlantRepository;
import org.enerscope.node.repository.FLNGUnitRepository;
import org.enerscope.node.repository.GatheringNetworkRepository;
import org.enerscope.node.repository.GroundBasedLiquefactionPlantRepository;
import org.enerscope.node.repository.LNGCarrierRepository;
import org.enerscope.node.repository.PipelineConnectionRepository;
import org.enerscope.node.repository.PipelineRepository;
import org.enerscope.node.repository.SeaportTerminalRepository;
import org.enerscope.node.repository.TreatmentPlantRepository;
import org.enerscope.node.repository.WellRepository;
import org.apache.commons.lang3.ObjectUtils.Null;
import org.enerscope.money.MoneyAmount;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.List;

/**
 * Implementation of the NodeService that handles creation of different node
 * types.
 * This implementation uses specific DTOs for each node type, providing clear
 * contracts
 * for what fields are required for each node type.
 */
@Service
public class NodeService {

    private static final UUID PROV_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Repositories for each node type
    private final WellRepository wellRepository;
    private final GatheringNetworkRepository gatheringNetworkRepository;
    private final TreatmentPlantRepository treatmentPlantRepository;
    private final PipelineRepository pipelineRepository;
    private final FLNGUnitRepository flngUnitRepository;
    private final GroundBasedLiquefactionPlantRepository gblpRepository;
    private final LNGCarrierRepository lngCarrierRepository;
    private final SeaportTerminalRepository seaportTerminalRepository;
    private final CompressingPlantRepository compressingPlantRepository;
    private final PipelineConnectionRepository pipelineConnectionRepository;

    public NodeService(
            WellRepository wellRepository,
            GatheringNetworkRepository gatheringNetworkRepository,
            TreatmentPlantRepository treatmentPlantRepository,
            PipelineRepository pipelineRepository,
            FLNGUnitRepository flngUnitRepository,
            GroundBasedLiquefactionPlantRepository gblpRepository,
            LNGCarrierRepository lngCarrierRepository,
            SeaportTerminalRepository seaportTerminalRepository,
            CompressingPlantRepository compressingPlantRepository,
            PipelineConnectionRepository pipelineConnectionRepository) {
        this.wellRepository = wellRepository;
        this.gatheringNetworkRepository = gatheringNetworkRepository;
        this.treatmentPlantRepository = treatmentPlantRepository;
        this.pipelineRepository = pipelineRepository;
        this.flngUnitRepository = flngUnitRepository;
        this.gblpRepository = gblpRepository;
        this.lngCarrierRepository = lngCarrierRepository;
        this.seaportTerminalRepository = seaportTerminalRepository;
        this.compressingPlantRepository = compressingPlantRepository;
        this.pipelineConnectionRepository = pipelineConnectionRepository;
    }

    private NodeTypeData DTOtoEntity(NodeTypeDataDTO data) {
        NodeTypeData typeData = new NodeTypeData(data.getVertical(), data.getRole(), data.getNodeType());
        return typeData;
    }

    private NodeGraphData DTOtoEntity(NodeGraphDataDTO data) {
        NodeGraphData graphData = new NodeGraphData(data.getXPosition(), data.getYPosition(), data.getCoordinates());
        return graphData;
    }

    private InvestmentCostComponent DTOtoEntity(InvestmentCostComponentDTO data) {
        InvestmentCostComponent component = new InvestmentCostComponent(data.getName(), data.getAmount(),
                data.getCostBasis());
        return component;
    }

    public InvestmentCost DTOtoEntity(InvestmentCostDTO data) {

        List<InvestmentCostComponent> componentEntities = data.getComponents().stream()
                .map(this::DTOtoEntity)
                .collect(Collectors.toList());

        InvestmentCost invCost = new InvestmentCost(componentEntities);
        return invCost;

    }

    public Well createWell(WellDTO data) {
        Well well = new Well(data.getName(), data.getState(), data.getStartupDate(), data.getLifespanInMonths(),
                data.getUpkeepCosts(),
                data.getMaintenanceIntervalInDays(), data.getOperatingCosts(), data.getWastePercentage(),
                this.DTOtoEntity(data.getInvestmentCost()), this.DTOtoEntity(data.getGraphData()), new NodeIdentity(),
                this.DTOtoEntity(data.getType()), data.getMaxCollectionCapacity(), data.getDeclineCurve(),
                data.getGasRichness(), data.getDTMTime(), data.getDTMCost());

        Well saved = wellRepository.save(well);

        return saved;
    }

    public GatheringNetworkDTO createGatheringNetwork(GatheringNetworkDTO gatheringNetworkDTO) {
        return gatheringNetworkDTO;
    }

    public TreatmentPlantDTO createTreatmentPlant(TreatmentPlantDTO treatmentPlantDTO) {
        return treatmentPlantDTO;
    }

    public PipelineDTO createPipeline(PipelineDTO pipelineDTO) {
        return pipelineDTO;
    }

    public PipelineConnectionDTO createPipelineConnection(PipelineConnectionDTO pipelineConnectionDTO) {
        return pipelineConnectionDTO;
    }

    public CompressingPlantDTO createCompressingPlant(CompressingPlantDTO compressingPlantDTO) {
        return compressingPlantDTO;
    }

    public GroundBasedLiquefactionPlantDTO createGroundBasedLiquefactionPlant(GroundBasedLiquefactionPlantDTO gblpDTO) {
        return gblpDTO;
    }

    public FLNGUnitDTO createFLNGUnit(FLNGUnitDTO flngUnitDTO) {
        return flngUnitDTO;
    }

    public LNGCarrierDTO createLNGCarrier(LNGCarrierDTO lngCarrierDTO) {
        return lngCarrierDTO;
    }

    public SeaportTerminalDTO createSeaportTerminal(SeaportTerminalDTO seaportTerminalDTO) {
        return seaportTerminalDTO;
    }

    public ConnectionDTO createConnection(ConnectionDTO connectionDTO) {
        return connectionDTO;
    }
}