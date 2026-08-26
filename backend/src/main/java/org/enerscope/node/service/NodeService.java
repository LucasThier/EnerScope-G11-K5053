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

   public NodeConnection editConnection(NodeConnection originalConnection, ConnectionDTO data) {

      originalConnection.setFromNodeId(data.getFromNodeId());
      originalConnection.setToNodeId(data.getToNodeId());

      NodeConnection saved = nodeConnectionRepository.save(originalConnection);

      return saved;
   }

   public Well editWell(Well well, WellDTO dto) {
      // Update basic fields inherited from BaseNode
      well.setName(dto.getName());
      well.setState(dto.getState());
      well.setStartupDate(dto.getStartupDate());
      well.setLifespanInMonths(dto.getLifespanInMonths());
      well.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      well.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      well.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      well.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         well.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         well.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         well.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         well.setIdentityId(dto.getIdentity());
      }

      // Update Well-specific fields
      well.setMaxCollectionCapacity(dto.getMaxCollectionCapacity());
      well.setDeclineCurve(dto.getDeclineCurve());
      well.setGasRichness(dto.getGasRichness());
      well.setDTMTime(dto.getDTMTime());
      well.setDTMCost(dto.getDTMCost() != null ? MoneyAmount.of(dto.getDTMCost()) : null);

      return well;
   }

   public GatheringNetwork editGatheringNetwork(GatheringNetwork network, GatheringNetworkDTO dto) {
      // Update basic fields inherited from BaseNode
      network.setName(dto.getName());
      network.setState(dto.getState());
      network.setStartupDate(dto.getStartupDate());
      network.setLifespanInMonths(dto.getLifespanInMonths());
      network.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      network.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      network.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      network.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         network.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         network.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         network.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         network.setIdentityId(dto.getIdentity());
      }

      // Update GatheringNetwork-specific fields
      network.setMaxTransportCapacity(dto.getMaxTransportCapacity());
      network.setLength(dto.getLength());
      network.setLossPerMeter(dto.getLossPerMeter());
      network.setConnectedWells(dto.getConnectedWells());

      return network;
   }

   public TreatmentPlant editTreatmentPlant(TreatmentPlant plant, TreatmentPlantDTO dto) {
      // Update basic fields inherited from BaseNode
      plant.setName(dto.getName());
      plant.setState(dto.getState());
      plant.setStartupDate(dto.getStartupDate());
      plant.setLifespanInMonths(dto.getLifespanInMonths());
      plant.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      plant.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      plant.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      plant.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         plant.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         plant.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         plant.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         plant.setIdentityId(dto.getIdentity());
      }

      // Update TreatmentPlant-specific fields
      plant.setMaxTreatmentCapacity(dto.getMaxTreatmentCapacity());
      plant.setContaminantWaste(dto.getContaminantWaste());
      plant.setIntermediateStorage(dto.getIntermediateStorage());
      plant.setTreatmentCost(MoneyAmount.of(dto.getTreatmentCost()));

      return plant;
   }

   public Pipeline editPipeline(Pipeline pipeline, PipelineDTO dto) {
      // Update basic fields inherited from BaseNode
      pipeline.setName(dto.getName());
      pipeline.setState(dto.getState());
      pipeline.setStartupDate(dto.getStartupDate());
      pipeline.setLifespanInMonths(dto.getLifespanInMonths());
      pipeline.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      pipeline.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      pipeline.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      pipeline.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         pipeline.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         pipeline.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         pipeline.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         pipeline.setIdentityId(dto.getIdentity());
      }

      // Update Pipeline-specific fields
      pipeline.setMaxFlowCapacity(dto.getMaxFlowCapacity());
      pipeline.setLength(dto.getLength());
      pipeline.setLossPerKm(dto.getLossPerKm());

      return pipeline;
   }

   public CompressingPlant editCompressingPlant(CompressingPlant plant, CompressingPlantDTO dto) {
      // Update basic fields inherited from BaseNode
      plant.setName(dto.getName());
      plant.setState(dto.getState());
      plant.setStartupDate(dto.getStartupDate());
      plant.setLifespanInMonths(dto.getLifespanInMonths());
      plant.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      plant.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      plant.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      plant.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         plant.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         plant.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         plant.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         plant.setIdentityId(dto.getIdentity());
      }

      // Update CompressingPlant-specific fields
      plant.setMaxCompressionCapacity(dto.getMaxCompressionCapacity());
      plant.setProcessWaste(dto.getProcessWaste());
      plant.setGasConsumption(dto.getGasConsumption());

      return plant;
   }

   public GroundBasedLiquefactionPlant editGroundBasedLiquefactionPlant(GroundBasedLiquefactionPlant plant, GroundBasedLiquefactionPlantDTO dto) {
      // Update basic fields inherited from BaseNode
      plant.setName(dto.getName());
      plant.setState(dto.getState());
      plant.setStartupDate(dto.getStartupDate());
      plant.setLifespanInMonths(dto.getLifespanInMonths());
      plant.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      plant.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      plant.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      plant.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         plant.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         plant.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         plant.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         plant.setIdentityId(dto.getIdentity());
      }

      // Update GroundBasedLiquefactionPlant-specific fields
      plant.setMaxProcessingCapacity(dto.getMaxProcessingCapacity());
      plant.setMTPARatio(dto.getMTPARatio());
      plant.setIntermediateStorage(dto.getIntermediateStorage());
      plant.setGasConsumption(dto.getGasConsumption());

      return plant;
   }

   public FLNGUnit editFLNGUnit(FLNGUnit unit, FLNGUnitDTO dto) {
      // Update basic fields inherited from BaseNode
      unit.setName(dto.getName());
      unit.setState(dto.getState());
      unit.setStartupDate(dto.getStartupDate());
      unit.setLifespanInMonths(dto.getLifespanInMonths());
      unit.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      unit.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      unit.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      unit.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         unit.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         unit.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         unit.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         unit.setIdentityId(dto.getIdentity());
      }

      // Update FLNGUnit-specific fields
      unit.setMaxProcessingCapacity(dto.getMaxProcessingCapacity());
      unit.setMTPARatio(dto.getMTPARatio());
      unit.setIntermediateStorage(dto.getIntermediateStorage());
      unit.setVesselDepth(dto.getVesselDepth());
      unit.setHiringCost(dto.getHiringCost() != null ? MoneyAmount.of(dto.getHiringCost()) : null);

      return unit;
   }

   public LNGCarrier editLNGCarrier(LNGCarrier carrier, LNGCarrierDTO dto) {
      // Update basic fields inherited from BaseNode
      carrier.setName(dto.getName());
      carrier.setState(dto.getState());
      carrier.setStartupDate(dto.getStartupDate());
      carrier.setLifespanInMonths(dto.getLifespanInMonths());
      carrier.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      carrier.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      carrier.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      carrier.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         carrier.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         carrier.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         carrier.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         carrier.setIdentityId(dto.getIdentity());
      }

      // Update LNGCarrier-specific fields
      carrier.setExportFrequency(dto.getExportFrequency());
      carrier.setShipCapacity(dto.getShipCapacity());
      carrier.setFullLoadTime(dto.getFullLoadTime());
      carrier.setHiringCost(dto.getHiringCost() != null ? MoneyAmount.of(dto.getHiringCost()) : null);
      carrier.setTimeToDestination(dto.getTimeToDestination());

      return carrier;
   }

   public SeaportTerminal editSeaportTerminal(SeaportTerminal terminal, SeaportTerminalDTO dto) {
      // Update basic fields inherited from BaseNode
      terminal.setName(dto.getName());
      terminal.setState(dto.getState());
      terminal.setStartupDate(dto.getStartupDate());
      terminal.setLifespanInMonths(dto.getLifespanInMonths());
      terminal.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      terminal.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      terminal.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      terminal.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         terminal.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         terminal.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         terminal.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         terminal.setIdentityId(dto.getIdentity());
      }

      // Update SeaportTerminal-specific fields
      terminal.setIntermediateStorage(dto.getIntermediateStorage());
      terminal.setPortDepth(dto.getPortDepth());
      terminal.setShipCapacity(dto.getShipCapacity());

      return terminal;
   }

   public PipelineConnection editPipelineConnection(PipelineConnection connection, PipelineConnectionDTO dto) {
      // Update basic fields inherited from BaseNode
      connection.setName(dto.getName());
      connection.setState(dto.getState());
      connection.setStartupDate(dto.getStartupDate());
      connection.setLifespanInMonths(dto.getLifespanInMonths());
      connection.setUpkeepCosts(dto.getUpkeepCosts() != null ? MoneyAmount.of(dto.getUpkeepCosts()) : null);
      connection.setMaintenanceIntervalInDays(dto.getMaintenanceIntervalInDays());
      connection.setOperatingCosts(dto.getOperatingCosts() != null ? MoneyAmount.of(dto.getOperatingCosts()) : null);
      connection.setWastePercentage(dto.getWastePercentage());

      // Update related entities
      if (dto.getInvestmentCost() != null) {
         connection.setInvestmentCost(DTOtoEntity(dto.getInvestmentCost()));
      }
      if (dto.getGraphData() != null) {
         connection.setGraphData(DTOtoEntity(dto.getGraphData()));
      }
      if (dto.getType() != null) {
         connection.setType(DTOtoEntity(dto.getType()));
      }
      if (dto.getIdentity() != null) {
         connection.setIdentityId(dto.getIdentity());
      }

      // Update PipelineConnection-specific fields
      connection.setTransferCapacity(dto.getTransferCapacity());
      connection.setOutputPriority(dto.getOutputPriority());

      return connection;
   }
}