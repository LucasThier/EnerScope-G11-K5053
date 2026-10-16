package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.node.repository.BaseNodeRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.node.service.NodeService;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

// Additional imports for node management
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.dto.DiagramConnectionDTO;
import org.enerscope.node.dto.DiagramDTO;
import org.enerscope.node.dto.DiagramNodeDTO;
import org.enerscope.node.dto.GeographicalPositionDTO;
import org.enerscope.node.dto.GraphPositionDTO;
import org.enerscope.node.dto.NodeBasicsDTO;
import org.enerscope.node.dto.NodeDetailDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.dto.NodeTypeDataDTO;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.GeographicalPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.money.MoneyAmount;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.enerscope.node.model.GeographicalPosition;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.dto.TreatmentPlantDTO;
import org.enerscope.node.dto.GatheringNetworkDTO;
import org.enerscope.node.dto.PipelineDTO;
import org.enerscope.node.dto.CompressingPlantDTO;
import org.enerscope.node.dto.GroundBasedLiquefactionPlantDTO;
import org.enerscope.node.dto.FLNGUnitDTO;
import org.enerscope.node.dto.LNGCarrierDTO;
import org.enerscope.node.dto.SeaportTerminalDTO;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;

@Service
@AllArgsConstructor
@Getter
public class VersionService {

    private final VersionRepository versionRepository;
    private final NodeConnectionRepository connectionRepository;
    private final BaseNodeRepository nodeRepository;
    private final AppLogger logger;
    private final NodeService nodeService;

    @Transactional
    public Version saveVersion(VersionDTO data) {
        if (data == null) {
            throw new IllegalArgumentException("VersionDTO cannot be null");
        }

        String name = data.getName();
        if (name == null) {
            throw new IllegalArgumentException("Version name cannot be null");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("Version name cannot be blank or whitespace only");
        }

        Version parentVersion = null;
        // A version starts with its own empty snapshots (never null so the
        // in-version ABM can append).
        List<BaseNode> nodeSnapshot = new ArrayList<>();
        List<NodeConnection> connectionSnapshot = new ArrayList<>();
        if (data.getParentVersion() != null) {
            parentVersion = versionRepository.findById(data.getParentVersion())
                    .orElseThrow(() -> new VersionNotFoundException(data.getParentVersion()));
            // Branch (git-style): deep-copy the parent's nodes and connections
            // into brand-new, independent rows so editing the branch never
            // touches the parent. Each cloned node keeps its cross-version
            // identity so branches can be compared later; connections are
            // remapped to the new node ids.
            Map<UUID, BaseNode> idMap = new HashMap<>();
            if (parentVersion.getNodeSnapshot() != null) {
                for (BaseNode src : parentVersion.getNodeSnapshot()) {
                    BaseNode saved = nodeRepository.save(cloneNodeForBranch(src));
                    idMap.put(src.getId(), saved);
                    nodeSnapshot.add(saved);
                }
            }
            if (parentVersion.getConnectionSnapshot() != null) {
                for (NodeConnection src : parentVersion.getConnectionSnapshot()) {
                    BaseNode newFrom = idMap.get(src.getFromNodeId());
                    BaseNode newTo = idMap.get(src.getToNodeId());
                    if (newFrom == null || newTo == null) {
                        continue;
                    }
                    NodeConnection saved = connectionRepository.save(
                            new NodeConnection(src.getIdentityId(), newFrom.getId(), newTo.getId()));
                    connectionSnapshot.add(saved);
                }
            }
        }

        Version version = new Version(data.getName(),
                parentVersion,
                nodeSnapshot,
                connectionSnapshot,
                new ArrayList<>(), new ArrayList<>());

        Version saved = versionRepository.save(version);

        logger.info("Registered the {} version", saved.getName());
        return saved;
    }

    public void deleteVersion(UUID id) {
        Objects.requireNonNull(id, "Version ID cannot be null");
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));
        // WE DELETE ALL OF THE SUB-VERSIONS, MAYBE CHANGE LATER

        deleteSubVersions(id);
        logger.info("Deleted version with id: {}", id);
    }

    public void deleteSubVersions(UUID parentVersion) {

        List<UUID> versionsToDelete = new ArrayList<>();
        Queue<UUID> queue = new LinkedList<>();
        queue.add(parentVersion);
        versionsToDelete.add(parentVersion);
        while (!queue.isEmpty()) {
            UUID currentId = queue.poll();
            List<Version> children = versionRepository.findByParentVersionId(currentId);

            for (Version child : children) {
                if (versionsToDelete.add(child.getId())) { // add returns true if not already present
                    queue.add(child.getId());
                }
            }
        }

        if (!versionsToDelete.isEmpty()) {
            Collections.reverse(versionsToDelete);
            versionRepository.deleteAllById(versionsToDelete);
            logger.info("Deleted {} versions in tree starting from root version ID: {}",
                    versionsToDelete.size(), parentVersion);
        }
    }

    public Version getVersion(UUID id) {
        Objects.requireNonNull(id, "Version ID cannot be null");
        return versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));
    }

    /**
     * Builds the flat diagram (nodes + connections) the editor renders for a
     * version. Runs in a read-only transaction so the lazy snapshot
     * associations are initialised before they are mapped to DTOs.
     */
    @Transactional(readOnly = true)
    public DiagramDTO getDiagram(UUID versionId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        List<DiagramNodeDTO> nodes = (version.getNodeSnapshot() == null
                ? List.<BaseNode>of()
                : version.getNodeSnapshot())
                .stream().map(this::toDiagramNode).toList();

        List<DiagramConnectionDTO> connections = (version.getConnectionSnapshot() == null
                ? List.<NodeConnection>of()
                : version.getConnectionSnapshot())
                .stream().map(this::toDiagramConnection).toList();

        return new DiagramDTO(versionId, nodes, connections);
    }

    /**
     * Presentation-only update of a node's position (diagram x/y and/or
     * geographical lng/lat). Used when the user drags a node on the canvas or
     * the map. It does not record a {@code NodeChange}: a move is not a
     * structural edit, and recording one per drag would be noise.
     */
    @Transactional
    public BaseNode updateNodePosition(UUID versionId, UUID nodeId, NodeGraphDataDTO positionDTO) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");
        Objects.requireNonNull(positionDTO, "Position DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        BaseNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));

        if (!version.getNodeSnapshot().contains(node)) {
            throw new IllegalArgumentException(
                    "Node with id " + nodeId + " does not exist in version " + versionId);
        }

        NodeGraphData graphData = node.getGraphData();
        if (graphData == null) {
            graphData = new NodeGraphData();
            node.setGraphData(graphData);
        }
        if (positionDTO.getGraphPosition() != null) {
            GraphPositionDTO gp = positionDTO.getGraphPosition();
            graphData.setGraphPosition(new GraphPosition(gp.getX(), gp.getY()));
        }
        if (positionDTO.getGeographicalPosition() != null) {
            GeographicalPositionDTO geo = positionDTO.getGeographicalPosition();
            graphData.setGeographicalPosition(new GeographicalPosition(geo.getLongitude(), geo.getLatitude()));
        }

        BaseNode saved = nodeRepository.save(node);
        logger.info("Updated position of node {} in version {}", nodeId, version.getName());
        return saved;
    }

    /**
     * Partial update of a node's basic fields (name / state) without touching
     * its type-specific data. Presentation/labelling change, so it records no
     * {@code NodeChange}.
     */
    @Transactional
    public BaseNode updateNodeBasics(UUID versionId, UUID nodeId, NodeBasicsDTO basics) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");
        Objects.requireNonNull(basics, "Basics DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        BaseNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));

        if (!version.getNodeSnapshot().contains(node)) {
            throw new IllegalArgumentException(
                    "Node with id " + nodeId + " does not exist in version " + versionId);
        }

        if (basics.getName() != null && !basics.getName().isBlank()) {
            node.setName(basics.getName());
        }
        if (basics.getState() != null) {
            node.setState(basics.getState());
        }

        BaseNode saved = nodeRepository.save(node);
        logger.info("Updated basics of node {} in version {}", nodeId, version.getName());
        return saved;
    }

    /**
     * Full detail of a node for the edit form: common fields plus the
     * type-specific values keyed by the frontend field names.
     */
    @Transactional(readOnly = true)
    public NodeDetailDTO getNodeDetail(UUID versionId, UUID nodeId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
        BaseNode node = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));
        if (!version.getNodeSnapshot().contains(node)) {
            throw new IllegalArgumentException(
                    "Node with id " + nodeId + " does not exist in version " + versionId);
        }

        NodeTypeDataDTO type = null;
        if (node.getType() != null) {
            NodeTypeData t = node.getType();
            type = new NodeTypeDataDTO(t.getVertical(), t.getRole(), t.getNodeType());
        }

        return new NodeDetailDTO(
                node.getId(), node.getIdentityId(), node.getName(), node.getState(),
                type, toGraphDataDTO(node.getGraphData()),
                money(node.getUpkeepCosts()), money(node.getOperatingCosts()),
                node.getLifespanInMonths(), node.getMaintenanceIntervalInDays(),
                (double) node.getWastePercentage(),
                typeAttributes(node));
    }

    private Map<String, Double> typeAttributes(BaseNode node) {
        Map<String, Double> a = new LinkedHashMap<>();
        if (node instanceof Well w) {
            a.put("maxCollectionCapacity", (double) w.getMaxCollectionCapacity());
            a.put("declineCurve", (double) w.getDeclineCurve());
            a.put("gasRichness", (double) w.getGasRichness());
            a.put("dtmTime", (double) w.getDTMTime());
            a.put("dtmCost", money(w.getDTMCost()));
            a.put("surface", (double) w.getSurface());
        } else if (node instanceof GatheringNetwork g) {
            a.put("maxTransportCapacity", (double) g.getMaxTransportCapacity());
            a.put("length", (double) g.getLength());
            a.put("lossPerMeter", (double) g.getLossPerMeter());
            a.put("connectedWells", (double) g.getConnectedWells());
        } else if (node instanceof TreatmentPlant t) {
            a.put("maxTreatmentCapacity", (double) t.getMaxTreatmentCapacity());
            a.put("contaminantWaste", 0.0); // not persisted on the entity
            a.put("intermediateStorage", (double) t.getIntermediateStorage());
            a.put("treatmentCost", money(t.getTreatmentCost()));
        } else if (node instanceof Pipeline p) {
            a.put("maxFlowCapacity", (double) p.getMaxFlowCapacity());
            a.put("length", (double) p.getLength());
            a.put("lossPerKm", (double) p.getLossPerKm());
        } else if (node instanceof PipelineConnection pc) {
            a.put("transferCapacity", (double) pc.getTransferCapacity());
            a.put("outputPriority", (double) pc.getOutputPriority());
        } else if (node instanceof CompressingPlant c) {
            a.put("maxCompressionCapacity", (double) c.getMaxCompressionCapacity());
            a.put("processWaste", (double) c.getProcessWaste());
            a.put("gasConsumption", (double) c.getGasConsumption());
        } else if (node instanceof GroundBasedLiquefactionPlant gb) {
            a.put("maxProcessingCapacity", (double) gb.getMaxProcessingCapacity());
            a.put("mtpaRatio", (double) gb.getMTPARatio());
            a.put("intermediateStorage", (double) gb.getIntermediateStorage());
            a.put("gasConsumption", (double) gb.getGasConsumption());
        } else if (node instanceof FLNGUnit f) {
            a.put("maxProcessingCapacity", (double) f.getMaxProcessingCapacity());
            a.put("mtpaRatio", (double) f.getMTPARatio());
            a.put("intermediateStorage", (double) f.getIntermediateStorage());
            a.put("vesselDepth", (double) f.getVesselDepth());
            a.put("hiringCost", money(f.getHiringCost()));
        } else if (node instanceof SeaportTerminal s) {
            a.put("intermediateStorage", (double) s.getIntermediateStorage());
            a.put("portDepth", (double) s.getPortDepth());
            a.put("shipCapacity", (double) s.getShipCapacity());
        } else if (node instanceof LNGCarrier l) {
            a.put("exportFrequency", (double) l.getExportFrequency());
            a.put("shipCapacity", (double) l.getShipCapacity());
            a.put("fullLoadTime", (double) l.getFullLoadTime());
            a.put("hiringCost", money(l.getHiringCost()));
            a.put("timeToDestination", (double) l.getTimeToDestination());
        }
        return a;
    }

    private static Double money(MoneyAmount amount) {
        return amount == null ? 0.0 : amount.value().doubleValue();
    }

    /**
     * Deep-clones a node into a new, independent transient entity for a branch:
     * copies every field except the primary key and audit timestamps, keeps the
     * cross-version {@code identityId}, and clones the owned one-to-one entities
     * (graph data, type, investment cost) so they become their own new rows.
     */
    private BaseNode cloneNodeForBranch(BaseNode src) {
        try {
            BaseNode copy = src.getClass().getDeclaredConstructor().newInstance();
            for (Class<?> c = src.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    int mod = f.getModifiers();
                    if (Modifier.isStatic(mod) || Modifier.isFinal(mod)) {
                        continue;
                    }
                    String name = f.getName();
                    if (name.equals("id") || name.equals("createdAt") || name.equals("lastModified")) {
                        continue;
                    }
                    f.setAccessible(true);
                    Object value = f.get(src);
                    if (value instanceof NodeGraphData g) {
                        value = cloneGraphData(g);
                    } else if (value instanceof NodeTypeData t) {
                        value = new NodeTypeData(t.getVertical(), t.getRole(), t.getNodeType());
                    } else if (value instanceof InvestmentCost ic) {
                        value = cloneInvestmentCost(ic);
                    }
                    f.set(copy, value);
                }
            }
            return copy;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not clone node for branch", e);
        }
    }

    private NodeGraphData cloneGraphData(NodeGraphData g) {
        GraphPosition gp = (g.getGraphPosition() == null)
                ? null
                : new GraphPosition(g.getGraphPosition().getX(), g.getGraphPosition().getY());
        GeographicalPosition geo = (g.getGeographicalPosition() == null)
                ? null
                : new GeographicalPosition(g.getGeographicalPosition().getLongitude(),
                        g.getGeographicalPosition().getLatitude());
        return new NodeGraphData(gp, geo);
    }

    private InvestmentCost cloneInvestmentCost(InvestmentCost ic) {
        List<InvestmentCostComponent> components = new ArrayList<>();
        if (ic.getComponents() != null) {
            for (InvestmentCostComponent c : ic.getComponents()) {
                components.add(new InvestmentCostComponent(c.getName(), c.getAmount(), c.getCostBasis()));
            }
        }
        return new InvestmentCost(components);
    }

    private NodeGraphDataDTO toGraphDataDTO(NodeGraphData g) {
        if (g == null) {
            return null;
        }
        GraphPositionDTO gp = (g.getGraphPosition() == null)
                ? null
                : new GraphPositionDTO(g.getGraphPosition().getX(), g.getGraphPosition().getY());
        GeographicalPositionDTO geo = (g.getGeographicalPosition() == null)
                ? null
                : new GeographicalPositionDTO(g.getGeographicalPosition().getLongitude(),
                        g.getGeographicalPosition().getLatitude());
        return new NodeGraphDataDTO(gp, geo);
    }

    private DiagramNodeDTO toDiagramNode(BaseNode node) {
        NodeTypeDataDTO type = null;
        if (node.getType() != null) {
            NodeTypeData t = node.getType();
            type = new NodeTypeDataDTO(t.getVertical(), t.getRole(), t.getNodeType());
        }

        return new DiagramNodeDTO(node.getId(), node.getIdentityId(), node.getName(), node.getState(), type,
                toGraphDataDTO(node.getGraphData()));
    }

    private DiagramConnectionDTO toDiagramConnection(NodeConnection connection) {
        return new DiagramConnectionDTO(connection.getId(), connection.getIdentityId(),
                connection.getFromNodeId(), connection.getToNodeId());
    }

    @Transactional
    public Version modifyVersion(UUID id, VersionDTO data) {
        Objects.requireNonNull(id, "Version ID cannot be null");
        Objects.requireNonNull(data, "VersionDTO cannot be null");

        Version existingVersion = versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));

        if (data.getParentVersion() != null) {
            Version parentVerison = versionRepository.findById(data.getParentVersion())
                    .orElseThrow(() -> new VersionNotFoundException(data.getParentVersion()));
            existingVersion.setParentVersion(parentVerison);
        }

        if (data.getName() != null && !data.getName().isBlank()) {
            existingVersion.setName(data.getName());
        }

        Version saved = versionRepository.save(existingVersion);
        logger.info("Modified version with id: {}", id);
        return saved;
    }

    @Transactional
    public BaseNode addNodeToVersion(UUID versionId, BaseNodeDTO nodeDTO) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeDTO, "Node DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        // A brand-new node gets a fresh cross-version identity. Only saveWell
        // defaulted this; centralise it here so every node type is covered and
        // the NOT NULL identity_id column is never violated.
        if (nodeDTO.getIdentity() == null) {
            nodeDTO.setIdentity(UUID.randomUUID());
        }

        BaseNode savedNode = switch (nodeDTO) {
            case WellDTO dto -> nodeService.saveWell(dto);
            case TreatmentPlantDTO dto -> nodeService.saveTreatmentPlant(dto);
            case GatheringNetworkDTO dto -> nodeService.saveGatheringNetwork(dto);
            case PipelineDTO dto -> nodeService.savePipeline(dto);
            case CompressingPlantDTO dto -> nodeService.saveCompressingPlant(dto);
            case GroundBasedLiquefactionPlantDTO dto -> nodeService.saveGroundBasedLiquefactionPlant(dto);
            case FLNGUnitDTO dto -> nodeService.saveFLNGUnit(dto);
            case LNGCarrierDTO dto -> nodeService.saveLNGCarrier(dto);
            case SeaportTerminalDTO dto -> nodeService.saveSeaportTerminal(dto);
            default ->
                throw new IllegalArgumentException("Unsupported node type: " + nodeDTO.getClass().getSimpleName());
        };

        version.getNodeSnapshot().add(savedNode);

        NodeChange nodeChange = new NodeChange();
        nodeChange.setChangeType(ChangeTypeEnum.ADD);
        nodeChange.setResultNode(savedNode);
        version.getNodeChanges().add(nodeChange);

        versionRepository.save(version);
        logger.info("Added {} to version {}", savedNode.getType().getNodeType(), version.getName());
        logger.info("number {}", version.getNodeSnapshot().size());

        return savedNode;
    }

    @Transactional
    public NodeConnection addConnectionToVersion(UUID versionId, ConnectionDTO connectionDTO) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(connectionDTO, "Connection DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        nodeRepository.findById(connectionDTO.getFromNodeId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Node not found with id: " + connectionDTO.getFromNodeId()));
        nodeRepository.findById(connectionDTO.getToNodeId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Node not found with id: " + connectionDTO.getToNodeId()));

        NodeConnection savedConnection = nodeService.saveConnection(connectionDTO);
        version.getConnectionSnapshot().add(savedConnection);

        ConnectionChange connectionChange = new ConnectionChange();
        connectionChange.setChangeType(ChangeTypeEnum.ADD);
        connectionChange.setChangedConnection(savedConnection);
        version.getConnectionChanges().add(connectionChange);

        versionRepository.save(version);
        logger.info("Added connection to version {}", version.getName());

        return savedConnection;
    }

    @Transactional
    public BaseNode editNodeInVersion(UUID versionId, UUID nodeId, BaseNodeDTO nodeDTO) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");
        Objects.requireNonNull(nodeDTO, "Node DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        BaseNode originalNode = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));

        // Check existing NodeChange records for this node
        List<NodeChange> nodeAddChanges = version.getNodeChanges().stream()
                .filter(change -> ChangeTypeEnum.ADD.equals(change.getChangeType())
                        && originalNode.equals(change.getChangedNode()))
                .collect(Collectors.toList());

        List<NodeChange> nodeEditChanges = version.getNodeChanges().stream()
                .filter(change -> ChangeTypeEnum.EDIT.equals(change.getChangeType())
                        && originalNode.equals(change.getChangedNode()))
                .collect(Collectors.toList());

        BaseNode editedNode;
        if (!nodeAddChanges.isEmpty()) {
            // Node was added in this version
            editedNode = editNodeByType(originalNode, nodeDTO);
            // The originalNode is already in the snapshot, and the edit methods modify it
            // in place.

            // Create and add NodeChange for EDIT
            NodeChange editChange = new NodeChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedNode(editedNode);
            editChange.setResultNode(editedNode);
            version.getNodeChanges().add(editChange);
        } else if (!nodeEditChanges.isEmpty()) {
            // Node was edited in this version (at least once before)
            editedNode = editNodeByType(originalNode, nodeDTO);
            // The editNodeByType method already updates the node, so no need to save again.

        } else {
            // No prior changes - node came from parent, create EDIT change
            // Remove original node from snapshot and add edited version
            version.getNodeSnapshot().remove(originalNode);
            editedNode = editNodeByType(originalNode, nodeDTO);
            version.getNodeSnapshot().add(editedNode);

            // Create and add NodeChange for EDIT
            NodeChange editChange = new NodeChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedNode(originalNode);
            editChange.setResultNode(editedNode);
            version.getNodeChanges().add(editChange);
        }

        versionRepository.save(version);
        logger.info("Edited node {} in version {}", nodeId, version.getName());

        return editedNode;
    }

    private BaseNode editNodeByType(BaseNode originalNode, BaseNodeDTO nodeDTO) {
        if (nodeDTO instanceof WellDTO dto) {
            if (!(originalNode instanceof Well)) {
                throw new IllegalArgumentException("Node type mismatch: expected Well");
            }
            return nodeService.editWell((Well) originalNode, dto);
        } else if (nodeDTO instanceof TreatmentPlantDTO dto) {
            if (!(originalNode instanceof TreatmentPlant)) {
                throw new IllegalArgumentException("Node type mismatch: expected TreatmentPlant");
            }
            return nodeService.editTreatmentPlant((TreatmentPlant) originalNode, dto);
        } else if (nodeDTO instanceof GatheringNetworkDTO dto) {
            if (!(originalNode instanceof GatheringNetwork)) {
                throw new IllegalArgumentException("Node type mismatch: expected GatheringNetwork");
            }
            return nodeService.editGatheringNetwork((GatheringNetwork) originalNode, dto);
        } else if (nodeDTO instanceof PipelineDTO dto) {
            if (!(originalNode instanceof Pipeline)) {
                throw new IllegalArgumentException("Node type mismatch: expected Pipeline");
            }
            return nodeService.editPipeline((Pipeline) originalNode, dto);
        } else if (nodeDTO instanceof CompressingPlantDTO dto) {
            if (!(originalNode instanceof CompressingPlant)) {
                throw new IllegalArgumentException("Node type mismatch: expected CompressingPlant");
            }
            return nodeService.editCompressingPlant((CompressingPlant) originalNode, dto);
        } else if (nodeDTO instanceof GroundBasedLiquefactionPlantDTO dto) {
            if (!(originalNode instanceof GroundBasedLiquefactionPlant)) {
                throw new IllegalArgumentException("Node type mismatch: expected GroundBasedLiquefactionPlant");
            }
            return nodeService.editGroundBasedLiquefactionPlant((GroundBasedLiquefactionPlant) originalNode, dto);
        } else if (nodeDTO instanceof FLNGUnitDTO dto) {
            if (!(originalNode instanceof FLNGUnit)) {
                throw new IllegalArgumentException("Node type mismatch: expected FLNGUnit");
            }
            return nodeService.editFLNGUnit((FLNGUnit) originalNode, dto);
        } else if (nodeDTO instanceof LNGCarrierDTO dto) {
            if (!(originalNode instanceof LNGCarrier)) {
                throw new IllegalArgumentException("Node type mismatch: expected LNGCarrier");
            }
            return nodeService.editLNGCarrier((LNGCarrier) originalNode, dto);
        } else if (nodeDTO instanceof SeaportTerminalDTO dto) {
            if (!(originalNode instanceof SeaportTerminal)) {
                throw new IllegalArgumentException("Node type mismatch: expected SeaportTerminal");
            }
            return nodeService.editSeaportTerminal((SeaportTerminal) originalNode, dto);
        } else {
            throw new IllegalArgumentException("Unsupported node DTO type: " + nodeDTO.getClass().getSimpleName());
        }
    }

    @Transactional
    public NodeConnection editConnectionInVersion(UUID versionId, UUID connectionId, ConnectionDTO connectionDTO) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(connectionId, "Connection ID cannot be null");
        Objects.requireNonNull(connectionDTO, "Connection DTO cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        NodeConnection originalConnection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("Connection not found with id: " + connectionId));

        if (!version.getConnectionSnapshot().contains(originalConnection)) {
            throw new IllegalArgumentException(
                    "Connection with id " + connectionId + " does not exist in version " + versionId);
        }

        // Check existing ConnectionChange records for this connection
        List<ConnectionChange> connectionAddChanges = version.getConnectionChanges().stream()
                .filter(change -> ChangeTypeEnum.ADD.equals(change.getChangeType())
                        && originalConnection.equals(change.getChangedConnection()))
                .collect(Collectors.toList());

        List<ConnectionChange> connectionEditChanges = version.getConnectionChanges().stream()
                .filter(change -> ChangeTypeEnum.EDIT.equals(change.getChangeType())
                        && originalConnection.equals(change.getChangedConnection()))
                .collect(Collectors.toList());

        if (!connectionAddChanges.isEmpty()) {
            // Connection was added in this version
            // EDIT
            NodeConnection editedConnection = nodeService.editConnection(originalConnection, connectionDTO);
            // The originalConnection is already in the snapshot, and the edit method
            // modifies it in place.
            // So the snapshot now reflects the changes.

            ConnectionChange editChange = new ConnectionChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedConnection(editedConnection);
            editChange.setResultConnection(editedConnection);
            version.getConnectionChanges().add(editChange);

            versionRepository.save(version);
            logger.info("Edited connection {} in version {}", connectionId, version.getName());

            return editedConnection;
        } else if (!connectionEditChanges.isEmpty()) {
            // Connection was edited in this version (at least once before)

            // EDIT
            NodeConnection editedConnection = nodeService.editConnection(originalConnection, connectionDTO);
            // The editConnection method already updates the connection, so no need to save
            // again.

            // Create and add ConnectionChange for EDIT
            ConnectionChange editChange = new ConnectionChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedConnection(editedConnection);
            editChange.setResultConnection(editedConnection);
            version.getConnectionChanges().add(editChange);

            versionRepository.save(version);
            logger.info("Edited connection {} in version {}", connectionId, version.getName());
            return editedConnection;

        } else {
            // No prior changes - connection came from parent, create EDIT change
            // Remove original connection from snapshot and add edited version
            version.getConnectionSnapshot().remove(originalConnection);
            NodeConnection editedConnection = nodeService.editConnection(originalConnection, connectionDTO);
            version.getConnectionSnapshot().add(editedConnection);

            // Create and add ConnectionChange for EDIT
            ConnectionChange editChange = new ConnectionChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedConnection(editedConnection);
            editChange.setResultConnection(editedConnection);
            version.getConnectionChanges().add(editChange);

            versionRepository.save(version);
            logger.info("Edited connection {} in version {}", connectionId, version.getName());
            return editedConnection;
        }

    }

    @Transactional
    public void deleteNodeFromVersion(UUID versionId, UUID nodeId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        // Handle regular node deletion
        BaseNode nodeToDelete = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));

        // Check if node exists in version's node snapshot
        boolean existsInNodeSnapshot = version.getNodeSnapshot().contains(nodeToDelete);

        if (!existsInNodeSnapshot) {
            throw new IllegalArgumentException(
                    "Node with id " + nodeId + " does not exist in version " + versionId);
        }

        // Check existing NodeChange records for this node
        List<NodeChange> nodeAddChanges = version.getNodeChanges().stream()
                .filter(change -> ChangeTypeEnum.ADD.equals(change.getChangeType())
                        && nodeToDelete.equals(change.getChangedNode()))
                .collect(Collectors.toList());

        List<NodeChange> nodeEditChanges = version.getNodeChanges().stream()
                .filter(change -> ChangeTypeEnum.EDIT.equals(change.getChangeType())
                        && nodeToDelete.equals(change.getChangedNode()))
                .collect(Collectors.toList());

        if (!nodeAddChanges.isEmpty()) {
            // Node was added in this version
            version.getNodeChanges().removeAll(nodeAddChanges);
            version.getNodeSnapshot().remove(nodeToDelete);
            nodeRepository.delete(nodeToDelete);
        } else if (!nodeEditChanges.isEmpty()) {
            // Node was edited in this version
            version.getNodeChanges().removeAll(nodeEditChanges);
            version.getNodeSnapshot().remove(nodeToDelete);
            nodeRepository.delete(nodeToDelete);

            NodeChange deleteChange = new NodeChange();
            deleteChange.setChangeType(ChangeTypeEnum.DELETE);
            deleteChange.setChangedNode(nodeToDelete);
            version.getNodeChanges().add(deleteChange);
        } else {
            // No prior changes - node came from parent, create DELETE change
            version.getNodeSnapshot().remove(nodeToDelete);

            NodeChange deleteChange = new NodeChange();
            deleteChange.setChangeType(ChangeTypeEnum.DELETE);
            deleteChange.setChangedNode(nodeToDelete);
            version.getNodeChanges().add(deleteChange);
        }

        versionRepository.save(version);
        logger.info("Deleted node {} from version {}", nodeId, version.getName());
    }

    @Transactional
    public void deleteConnectionFromVersion(UUID versionId, UUID connectionId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(connectionId, "Connection ID cannot be null");

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        // Handle connection deletion
        NodeConnection connectionToDelete = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("Connection not found with id: " + connectionId));

        // Check if connection exists in version's connection snapshot
        boolean existsInConnectionSnapshot = version.getConnectionSnapshot().contains(connectionToDelete);

        if (!existsInConnectionSnapshot) {
            throw new IllegalArgumentException(
                    "Connection with id " + connectionId + " does not exist in version " + versionId);
        }

        // Check existing ConnectionChange records for this connection
        List<ConnectionChange> connectionAddChanges = version.getConnectionChanges().stream()
                .filter(change -> ChangeTypeEnum.ADD.equals(change.getChangeType())
                        && connectionToDelete.equals(change.getChangedConnection()))
                .collect(Collectors.toList());

        List<ConnectionChange> connectionEditChanges = version.getConnectionChanges().stream()
                .filter(change -> ChangeTypeEnum.EDIT.equals(change.getChangeType())
                        && connectionToDelete.equals(change.getChangedConnection()))
                .collect(Collectors.toList());

        if (!connectionAddChanges.isEmpty()) {
            // Connection was added in this version
            version.getConnectionChanges().removeAll(connectionAddChanges);
            version.getConnectionSnapshot().remove(connectionToDelete);
            connectionRepository.delete(connectionToDelete);
        } else if (!connectionEditChanges.isEmpty()) {
            // Connection was edited in this version, but also deleted
            version.getConnectionChanges().removeAll(connectionEditChanges);
            version.getConnectionSnapshot().remove(connectionToDelete);
            connectionRepository.delete(connectionToDelete);

            ConnectionChange deleteChange = new ConnectionChange();
            deleteChange.setChangeType(ChangeTypeEnum.DELETE);
            deleteChange.setResultConnection(connectionToDelete);
            version.getConnectionChanges().add(deleteChange);
        } else {
            // No prior changes - connection came from parent, create DELETE change
            version.getConnectionSnapshot().remove(connectionToDelete);

            ConnectionChange deleteChange = new ConnectionChange();
            deleteChange.setChangeType(ChangeTypeEnum.DELETE);
            deleteChange.setResultConnection(connectionToDelete);
            version.getConnectionChanges().add(deleteChange);
        }

        versionRepository.save(version);
        logger.info("Deleted connection {} from version {}", connectionId, version.getName());
    }
}
