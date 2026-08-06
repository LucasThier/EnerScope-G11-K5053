package org.enerscope.node.service;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.*;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeGraphData;
import java.util.UUID;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.repository.CompressingPlantRepository;
import org.enerscope.node.repository.FLNGUnitRepository;
import org.enerscope.node.repository.GatheringNetworkRepository;
import org.enerscope.node.repository.GroundBasedLiquefactionPlantRepository;
import org.enerscope.node.repository.LNGCarrierRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.node.repository.PipelineConnectionRepository;
import org.enerscope.node.repository.PipelineRepository;
import org.enerscope.node.repository.SeaportTerminalRepository;
import org.enerscope.node.repository.TreatmentPlantRepository;
import org.enerscope.node.repository.WellRepository;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;
import java.util.List;

//Implementation of the NodeService that handles creation of different node types.This implementation uses specific DTOs for each node type,providing clear contracts for what fields are required for each node type./

@Service
public class NodeService {

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
   private final NodeConnectionRepository nodeConnectionRepository;

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
         PipelineConnectionRepository pipelineConnectionRepository,
         NodeConnectionRepository nodeConnectionRepository) {
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
      this.nodeConnectionRepository = nodeConnectionRepository;
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
      InvestmentCostComponent component = new InvestmentCostComponent(data.getName(), MoneyAmount.of(data.getAmount()),
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

   public Well saveWell(WellDTO data) {
      Well well = new Well(data.getName(), data.getState(), data.getStartupDate(), data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()), this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxCollectionCapacity(), data.getDeclineCurve(),
            data.getGasRichness(), data.getDTMTime(), MoneyAmount.of(data.getDTMCost()));

      Well saved = wellRepository.save(well);

      return saved;
   }

   public GatheringNetwork saveGatheringNetwork(GatheringNetworkDTO data) {
      GatheringNetwork gatheringNetwork = new GatheringNetwork(data.getName(),
            data.getState(), data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxTransportCapacity(),
            data.getLength(),
            data.getLossPerMeter(),
            data.getConnectedWells());

      GatheringNetwork saved = gatheringNetworkRepository.save(gatheringNetwork);

      return saved;
   }

   public TreatmentPlant saveTreatmentPlant(TreatmentPlantDTO data) {
      TreatmentPlant treatmentPlant = new TreatmentPlant(data.getName(),
            data.getState(), data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxTreatmentCapacity(),
            data.getContaminantWaste(),
            data.getIntermediateStorage(), MoneyAmount.of(data.getTreatmentCost()));

      TreatmentPlant saved = treatmentPlantRepository.save(treatmentPlant);

      return saved;
   }

   public Pipeline savePipeline(PipelineDTO data) {
      Pipeline pipeline = new Pipeline(data.getName(), data.getState(),
            data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxFlowCapacity(),
            data.getLength(), data.getLossPerKm());

      Pipeline saved = pipelineRepository.save(pipeline);

      return saved;
   }

   public PipelineConnection savePipelineConnection(PipelineConnectionDTO data) {
      PipelineConnection pipelineConnection = new PipelineConnection(data.getName(), data.getState(),
            data.getStartupDate(), data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getTransferCapacity(),
            data.getOutputPriority());

      PipelineConnection saved = pipelineConnectionRepository.save(pipelineConnection);

      return saved;
   }

   public CompressingPlant saveCompressingPlant(CompressingPlantDTO data) {
      CompressingPlant compressingPlant = new CompressingPlant(data.getName(),
            data.getState(), data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxCompressionCapacity(),
            data.getProcessWaste(),
            data.getGasConsumption());

      CompressingPlant saved = compressingPlantRepository.save(compressingPlant);

      return saved;
   }

   public GroundBasedLiquefactionPlant saveGroundBasedLiquefactionPlant(GroundBasedLiquefactionPlantDTO data) {
      GroundBasedLiquefactionPlant groundBasedLiquefactionPlant = new GroundBasedLiquefactionPlant(data.getName(),
            data.getState(), data.getStartupDate(), data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxProcessingCapacity(),
            data.getMTPARatio(),
            data.getIntermediateStorage(), data.getGasConsumption());

      GroundBasedLiquefactionPlant saved = gblpRepository.save(groundBasedLiquefactionPlant);

      return saved;
   }

   public FLNGUnit saveFLNGUnit(FLNGUnitDTO data) {
      FLNGUnit flngUnit = new FLNGUnit(data.getName(), data.getState(),
            data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getMaxProcessingCapacity(),
            data.getMTPARatio(),
            data.getIntermediateStorage(),
            data.getVesselDepth(), MoneyAmount.of(data.getHiringCost()));

      FLNGUnit saved = flngUnitRepository.save(flngUnit);

      return saved;
   }

   public LNGCarrier saveLNGCarrier(LNGCarrierDTO data) {
      LNGCarrier lngCarrier = new LNGCarrier(data.getName(), data.getState(),
            data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getExportFrequency(),
            data.getShipCapacity(),
            data.getFullLoadTime(),
            MoneyAmount.of(data.getHiringCost()), data.getTimeToDestination());

      LNGCarrier saved = lngCarrierRepository.save(lngCarrier);

      return saved;
   }

   public SeaportTerminal saveSeaportTerminal(SeaportTerminalDTO data) {
      SeaportTerminal seaportTerminal = new SeaportTerminal(data.getName(),
            data.getState(), data.getStartupDate(),
            data.getLifespanInMonths(),
            MoneyAmount.of(data.getUpkeepCosts()),
            data.getMaintenanceIntervalInDays(), MoneyAmount.of(data.getOperatingCosts()),
            data.getWastePercentage(),
            this.DTOtoEntity(data.getInvestmentCost()),
            this.DTOtoEntity(data.getGraphData()),
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            this.DTOtoEntity(data.getType()), data.getIntermediateStorage(),
            data.getPortDepth(),
            data.getShipCapacity());

      SeaportTerminal saved = seaportTerminalRepository.save(seaportTerminal);

      return saved;
   }

   public NodeConnection saveConnection(ConnectionDTO data) {

      NodeConnection connection = new NodeConnection(
            (data.getIdentity() != null) ? data.getIdentity() : UUID.randomUUID(),
            data.getFromNodeId(), data.getToNodeId());

      NodeConnection saved = nodeConnectionRepository.save(connection);

      return saved;
   }
}