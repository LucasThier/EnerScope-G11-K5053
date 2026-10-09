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
import org.enerscope.node.service.NodeCloner;
import org.enerscope.node.service.NodeService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
import org.enerscope.project.service.ProjectAccessGuard;

@Service
@AllArgsConstructor
@Getter
public class VersionService {

    private final VersionRepository versionRepository;
    private final NodeConnectionRepository connectionRepository;
    private final BaseNodeRepository nodeRepository;
    private final AppLogger logger;
    private final NodeService nodeService;
    private final VersionConflictService versionConflictService;
    private final ProjectAccessGuard accessGuard;

    /**
     * Creates a version detached from every project, for
     * {@code POST /version/createtest}. A version with no owning project has no
     * membership that could grant access to it, so only a platform ADMIN may
     * make one — and, by the same rule in {@link ProjectAccessGuard}, only a
     * platform ADMIN can touch it afterwards.
     */
    public Version saveOrphanVersion(VersionDTO data) {
        accessGuard.assertIsPlatformAdmin("create detached versions");
        return saveVersion(data);
    }

    /**
     * Creates a version. Deliberately unguarded: it is reached either through
     * {@code ProjectService.saveVersion}, which authorizes the owning project
     * before calling it, or through {@link #saveOrphanVersion}, which requires a
     * platform ADMIN. There is no version id to check yet at this point.
     */
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
        // Always start from an empty (never null) list: addNodeToVersion/addConnectionToVersion
        // call .add() directly on these without a null check, and a null snapshot would NPE
        // the very first time a node/connection is added to a version that has no parent yet.
        // A version starts with empty snapshots, never null ones: every
        // getNodeSnapshot()/getConnectionSnapshot() call in this class mutates
        // the list in place, and the create response would otherwise answer
        // "nodeSnapshot": null where a later read answers [].
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
        accessGuard.assertCanEditVersion(id);
        Version version = versionRepository.findById(id)
                .orElseThrow(() -> new VersionNotFoundException(id));
        // WE DELETE ALL OF THE SUB-VERSIONS, MAYBE CHANGE LATER

        deleteSubVersions(id);
        logger.info("Deleted version with id: {}", id);
    }

    /**
     * Deletes a version and everything below it. Private on purpose: it is only
     * reached through {@link #deleteVersion}, which runs the permission check —
     * a public entry point here would be an unguarded way to delete a tree.
     *
     * <p>Known limitation: the permission check covers the root version's
     * project. A child version attached to a different project would be deleted
     * without that project being checked; see {@code docs/considerations.md}.</p>
     */
    private void deleteSubVersions(UUID parentVersion) {

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
        accessGuard.assertCanViewVersion(id);
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
        accessGuard.assertCanEditVersion(id);

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
        accessGuard.assertCanEditVersion(versionId);

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

        // Guard against ending up with two rows for one identity in this version's
        // own snapshot (e.g. a caller reusing an explicit identity that's already
        // present) - same invariant enforced everywhere else identity-based.
        UUID identityId = savedNode.getIdentityId();
        version.getNodeSnapshot()
                .removeIf(existing -> existing != null && identityId != null
                        && identityId.equals(existing.getIdentityId()));
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
        accessGuard.assertCanEditVersion(versionId);

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        nodeRepository.findById(connectionDTO.getFromNodeId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Node not found with id: " + connectionDTO.getFromNodeId()));
        nodeRepository.findById(connectionDTO.getToNodeId())
                .orElseThrow(
                        () -> new EntityNotFoundException("Node not found with id: " + connectionDTO.getToNodeId()));

        NodeConnection savedConnection = nodeService.saveConnection(connectionDTO);

        // Guard against ending up with two rows for one identity in this version's
        // own snapshot, same as addNodeToVersion.
        UUID connectionIdentityId = savedConnection.getIdentityId();
        version.getConnectionSnapshot()
                .removeIf(existing -> existing != null && connectionIdentityId != null
                        && connectionIdentityId.equals(existing.getIdentityId()));
        version.getConnectionSnapshot().add(savedConnection);

        ConnectionChange connectionChange = new ConnectionChange();
        connectionChange.setChangeType(ChangeTypeEnum.ADD);
        // resultConnection (not changedConnection) holds the new row for ADD -
        // matches addNodeToVersion, and is what applyConnectionChangesToSnapshot/
        // identityOfChange rely on to tell "added" apart from "deleted".
        connectionChange.setResultConnection(savedConnection);
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
        accessGuard.assertCanEditVersion(versionId);

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        BaseNode requestedNode = nodeRepository.findById(nodeId)
                .orElseThrow(() -> new EntityNotFoundException("Node not found with id: " + nodeId));
        UUID identityId = requestedNode.getIdentityId();

        // Resolve the node to actually mutate from this version's OWN current
        // snapshot, by identity - never trust nodeId to already be this version's
        // current row for that identity. The caller may have passed a stale id
        // (e.g. from before a merge changed what this version now holds); acting
        // on nodeId directly would leave the real current row in the snapshot
        // untouched while adding another one for the same identity, producing two
        // rows for one identityId.
        BaseNode currentNode = findNodeByIdentity(version.getNodeSnapshot(), identityId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Node with identity " + identityId + " does not exist in version " + versionId));

        // Does this version already have its own row for this identity (from an
        // earlier add/edit in this version)? Matched by identityId, not by object
        // equality: a NodeChange's changedNode/resultNode may not be the same Java
        // instance as currentNode (different Hibernate session).
        boolean versionOwnsIdentity = version.getNodeChanges().stream()
                .anyMatch(change -> matchesIdentity(change.getChangedNode(), identityId)
                        || matchesIdentity(change.getResultNode(), identityId));

        BaseNode editedNode;
        if (versionOwnsIdentity) {
            // This version already owns an exclusive row for this identity (not
            // shared with its parent or any sibling) - safe to mutate in place.
            editedNode = editNodeByType(currentNode, nodeDTO);
        } else {
            // First time this version touches this identity: currentNode is the
            // row inherited from (and still shared with) the parent. Never mutate
            // it in place - clone it first, so the parent's/siblings' snapshots
            // keep seeing the original, untouched data.
            version.getNodeSnapshot().remove(currentNode);
            BaseNode clone = NodeCloner.cloneNode(currentNode);
            editedNode = editNodeByType(clone, nodeDTO);
            nodeRepository.save(editedNode);
            version.getNodeSnapshot().add(editedNode);

            NodeChange editChange = new NodeChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedNode(currentNode);
            editChange.setResultNode(editedNode);
            version.getNodeChanges().add(editChange);
        }

        versionRepository.save(version);
        logger.info("Edited node {} in version {}", nodeId, version.getName());

        return editedNode;
    }

    private boolean matchesIdentity(BaseNode node, UUID identityId) {
        return node != null && identityId != null && identityId.equals(node.getIdentityId());
    }

    private java.util.Optional<BaseNode> findNodeByIdentity(List<BaseNode> nodes, UUID identityId) {
        if (nodes == null) {
            return java.util.Optional.empty();
        }
        return nodes.stream().filter(node -> matchesIdentity(node, identityId)).findFirst();
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
        accessGuard.assertCanEditVersion(versionId);

        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));

        NodeConnection requestedConnection = connectionRepository.findById(connectionId)
                .orElseThrow(() -> new EntityNotFoundException("Connection not found with id: " + connectionId));
        UUID identityId = requestedConnection.getIdentityId();

        // Same principle as editNodeInVersion: resolve the connection to mutate
        // from this version's OWN current snapshot, by identity, never trusting
        // connectionId to already be this version's current row - otherwise a
        // stale id would leave the real current row untouched while adding
        // another one for the same identity.
        NodeConnection currentConnection = findConnectionByIdentity(version.getConnectionSnapshot(), identityId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Connection with identity " + identityId + " does not exist in version " + versionId));

        // Same clone-on-write rule as editNodeInVersion: only mutate in place if
        // this version already owns its own row for this identity.
        boolean versionOwnsIdentity = version.getConnectionChanges().stream()
                .anyMatch(change -> matchesIdentity(change.getChangedConnection(), identityId)
                        || matchesIdentity(change.getResultConnection(), identityId));

        NodeConnection editedConnection;
        if (versionOwnsIdentity) {
            editedConnection = nodeService.editConnection(currentConnection, connectionDTO);
        } else {
            version.getConnectionSnapshot().remove(currentConnection);
            NodeConnection clone = NodeCloner.cloneConnection(currentConnection);
            editedConnection = nodeService.editConnection(clone, connectionDTO);
            connectionRepository.save(editedConnection);
            version.getConnectionSnapshot().add(editedConnection);

            ConnectionChange editChange = new ConnectionChange();
            editChange.setChangeType(ChangeTypeEnum.EDIT);
            editChange.setChangedConnection(currentConnection);
            editChange.setResultConnection(editedConnection);
            version.getConnectionChanges().add(editChange);
        }

        versionRepository.save(version);
        logger.info("Edited connection {} in version {}", connectionId, version.getName());
        return editedConnection;
    }

    private boolean matchesIdentity(NodeConnection connection, UUID identityId) {
        return connection != null && identityId != null && identityId.equals(connection.getIdentityId());
    }

    private java.util.Optional<NodeConnection> findConnectionByIdentity(List<NodeConnection> connections,
            UUID identityId) {
        if (connections == null) {
            return java.util.Optional.empty();
        }
        return connections.stream().filter(connection -> matchesIdentity(connection, identityId)).findFirst();
    }

    @Transactional
    public void deleteNodeFromVersion(UUID versionId, UUID nodeId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        Objects.requireNonNull(nodeId, "Node ID cannot be null");

        accessGuard.assertCanEditVersion(versionId);

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

        accessGuard.assertCanEditVersion(versionId);

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

    @Transactional
    public Version mergeSubVersionIntoParent(UUID subVersionId) {
        Objects.requireNonNull(subVersionId, "Subversion ID cannot be null");

        Version subVersion = versionRepository.findById(subVersionId)
                .orElseThrow(() -> new VersionNotFoundException(subVersionId));
        Version parentVersion = versionRepository.findById(subVersion.getParentVersion().getId())
                .orElseThrow(() -> new VersionNotFoundException(subVersion.getParentVersion().getId()));

        // Validate that subVersion actually has parentVersion as its parent
        if (!Objects.equals(subVersion.getParentVersion(), parentVersion)) {
            throw new IllegalArgumentException("Subversion does not have the specified parent version");
        }

        // Apply the subversion's own differential changes onto the parent's
        // CURRENT snapshot, instead of wholesale-replacing it - a wholesale
        // replace would silently discard anything the parent already had that
        // this particular subversion never touched (e.g. a node another,
        // already-merged branch added).
        parentVersion.setNodeSnapshot(
                applyNodeChangesToSnapshot(parentVersion.getNodeSnapshot(), subVersion.getNodeChanges()));
        parentVersion.setConnectionSnapshot(
                applyConnectionChangesToSnapshot(parentVersion.getConnectionSnapshot(), subVersion.getConnectionChanges()));

        // Merge change lists
        List<NodeChange> nodechanges = new ArrayList<>(parentVersion.getNodeChanges());
        parentVersion.getNodeChanges().clear();
        parentVersion.getNodeChanges().addAll(
                mergeNodeChanges(nodechanges, subVersion.getNodeChanges()));

        List<ConnectionChange> connectionchanges = new ArrayList<>(parentVersion.getConnectionChanges());
        parentVersion.getConnectionChanges().clear();
        parentVersion.getConnectionChanges().addAll(
                mergeConnectionChanges(connectionchanges, subVersion.getConnectionChanges()));
        Version saved = versionRepository.save(parentVersion);
        logger.info("Merged subversion {} into parent version {}", subVersion.getName(), parentVersion.getName());

        versionConflictService.recordMergeConflicts(saved, subVersion);

        return saved;
    }

    /**
     * Applies each of {@code changes} onto a copy of {@code baseSnapshot} as an
     * upsert-or-remove keyed by identity: a change with a {@code resultNode}
     * (ADD or EDIT) replaces whatever this snapshot currently has for that
     * identity with it; a change with no {@code resultNode} (DELETE) removes
     * that identity. Entities the changes never touch are left exactly as they
     * were - unlike replacing the whole snapshot, this can never discard
     * something the base already had that {@code changes} doesn't mention.
     */
    private List<BaseNode> applyNodeChangesToSnapshot(List<BaseNode> baseSnapshot, List<NodeChange> changes) {
        List<BaseNode> result = new ArrayList<>(baseSnapshot == null ? Collections.emptyList() : baseSnapshot);
        if (changes == null) {
            return result;
        }
        for (NodeChange change : changes) {
            UUID identityId = identityOfChange(change);
            if (identityId == null) {
                continue;
            }
            result.removeIf(node -> identityId.equals(node.getIdentityId()));
            if (change.getResultNode() != null) {
                result.add(change.getResultNode());
            }
        }
        return result;
    }

    /** Same rule as {@link #applyNodeChangesToSnapshot} but for connections. */
    private List<NodeConnection> applyConnectionChangesToSnapshot(List<NodeConnection> baseSnapshot,
            List<ConnectionChange> changes) {
        List<NodeConnection> result = new ArrayList<>(baseSnapshot == null ? Collections.emptyList() : baseSnapshot);
        if (changes == null) {
            return result;
        }
        for (ConnectionChange change : changes) {
            UUID identityId = identityOfChange(change);
            if (identityId == null) {
                continue;
            }
            result.removeIf(connection -> identityId.equals(connection.getIdentityId()));
            if (change.getResultConnection() != null) {
                result.add(change.getResultConnection());
            }
        }
        return result;
    }

    private UUID identityOfChange(NodeChange change) {
        if (change.getResultNode() != null) {
            return change.getResultNode().getIdentityId();
        }
        if (change.getChangedNode() != null) {
            return change.getChangedNode().getIdentityId();
        }
        return null;
    }

    private UUID identityOfChange(ConnectionChange change) {
        if (change.getResultConnection() != null) {
            return change.getResultConnection().getIdentityId();
        }
        if (change.getChangedConnection() != null) {
            return change.getChangedConnection().getIdentityId();
        }
        return null;
    }

    /**
     * Merges two lists of NodeChanges according to merge rules:
     * - ADD + DELETE cancels out (both removed)
     * - EDIT + EDIT produces single EDIT with parent's changedNode and subversion's
     * resultNode
     * - Other combinations are preserved
     */
    private List<NodeChange> mergeNodeChanges(List<NodeChange> parentChanges, List<NodeChange> subChanges) {
        List<NodeChange> result = new ArrayList<>();

        if (parentChanges == null)
            parentChanges = Collections.emptyList();
        if (subChanges == null)
            subChanges = Collections.emptyList();

        // Group changes by node ID
        Map<UUID, List<NodeChange>> parentChangesByNode = groupChangesByNode(parentChanges);
        Map<UUID, List<NodeChange>> subChangesByNode = groupChangesByNode(subChanges);

        // Get all unique node IDs from both versions
        Set<UUID> allNodeIds = new HashSet<>();
        allNodeIds.addAll(parentChangesByNode.keySet());
        allNodeIds.addAll(subChangesByNode.keySet());

        for (UUID nodeId : allNodeIds) {
            List<NodeChange> parentNodeChanges = parentChangesByNode.getOrDefault(nodeId, Collections.emptyList());
            List<NodeChange> subNodeChanges = subChangesByNode.getOrDefault(nodeId, Collections.emptyList());

            List<NodeChange> merged = mergeNodeChangesForNode(parentNodeChanges, subNodeChanges);
            result.addAll(merged);
        }

        return result;
    }

    /**
     * Groups NodeChanges by the identity of the node they affect. Grouping by
     * raw node id would never match across merges: clone-on-write gives every
     * edit a fresh id, so the same conceptual node has a different id in each
     * side's changes. Only identityId is stable across edits/merges.
     */
    private Map<UUID, List<NodeChange>> groupChangesByNode(List<NodeChange> changes) {
        Map<UUID, List<NodeChange>> grouped = new HashMap<>();
        for (NodeChange change : changes) {
            UUID identityId = identityOfChange(change);
            if (identityId != null) {
                grouped.computeIfAbsent(identityId, k -> new ArrayList<>()).add(change);
            }
        }
        return grouped;
    }

    /**
     * Reduces every NodeChange recorded for one identity (the parent's already
     * -accumulated history, followed by this merge's own new changes, in that
     * chronological order) to at most one net change: the state right before
     * this whole history began ({@code origin}, from the very first entry -
     * null if the identity didn't exist yet) versus the state it nets out to
     * now ({@code current} - null if the net effect is a deletion). Folding
     * the whole chain instead of only ever looking at exactly two entries is
     * what makes this safe to call across any number of merges: without it,
     * a node changed across three separate merges would end up with three
     * stale entries (e.g. ADD, EDIT, ADD) instead of collapsing to one, since
     * every clone-on-write edit gets a fresh id and nothing would ever look
     * like a "matching pair" again.
     */
    private List<NodeChange> mergeNodeChangesForNode(List<NodeChange> parentChanges, List<NodeChange> subChanges) {
        List<NodeChange> combined = new ArrayList<>(parentChanges.size() + subChanges.size());
        combined.addAll(parentChanges);
        combined.addAll(subChanges);
        if (combined.isEmpty()) {
            return Collections.emptyList();
        }

        BaseNode origin = combined.get(0).getChangedNode();
        BaseNode current = null;
        for (NodeChange change : combined) {
            current = ChangeTypeEnum.DELETE.equals(change.getChangeType()) ? null : change.getResultNode();
        }

        if (origin == null && current == null) {
            return Collections.emptyList(); // Added and deleted within this combined history - nets to nothing.
        }

        NodeChange net = new NodeChange();
        net.setChangedNode(origin);
        net.setResultNode(current);
        net.setChangeType(current == null ? ChangeTypeEnum.DELETE
                : origin == null ? ChangeTypeEnum.ADD : ChangeTypeEnum.EDIT);
        return List.of(net);
    }

    /**
     * Merges two lists of ConnectionChanges according to merge rules:
     * - ADD + DELETE cancels out (both removed)
     * - EDIT + EDIT produces single EDIT with parent's changedConnection and
     * subversion's resultConnection
     * - Other combinations are preserved
     */
    private List<ConnectionChange> mergeConnectionChanges(List<ConnectionChange> parentChanges,
            List<ConnectionChange> subChanges) {
        List<ConnectionChange> result = new ArrayList<>();

        if (parentChanges == null)
            parentChanges = Collections.emptyList();
        if (subChanges == null)
            subChanges = Collections.emptyList();

        // Group changes by connection ID
        Map<UUID, List<ConnectionChange>> parentChangesByConnection = groupChangesByConnection(parentChanges);
        Map<UUID, List<ConnectionChange>> subChangesByConnection = groupChangesByConnection(subChanges);

        // Get all unique connection IDs from both versions
        Set<UUID> allConnectionIds = new HashSet<>();
        allConnectionIds.addAll(parentChangesByConnection.keySet());
        allConnectionIds.addAll(subChangesByConnection.keySet());

        for (UUID connectionId : allConnectionIds) {
            List<ConnectionChange> parentConnChanges = parentChangesByConnection.getOrDefault(connectionId,
                    Collections.emptyList());
            List<ConnectionChange> subConnChanges = subChangesByConnection.getOrDefault(connectionId,
                    Collections.emptyList());

            List<ConnectionChange> merged = mergeConnectionChangesForConnection(parentConnChanges, subConnChanges);
            result.addAll(merged);
        }

        return result;
    }

    /**
     * Groups ConnectionChanges by the identity of the connection they affect
     * (see {@link #groupChangesByNode} for why identity, not raw id, is the
     * correct key).
     */
    private Map<UUID, List<ConnectionChange>> groupChangesByConnection(List<ConnectionChange> changes) {
        Map<UUID, List<ConnectionChange>> grouped = new HashMap<>();
        for (ConnectionChange change : changes) {
            UUID identityId = identityOfChange(change);
            if (identityId != null) {
                grouped.computeIfAbsent(identityId, k -> new ArrayList<>()).add(change);
            }
        }
        return grouped;
    }

    /**
     * Same fold as {@link #mergeNodeChangesForNode}, for connections.
     */
    private List<ConnectionChange> mergeConnectionChangesForConnection(List<ConnectionChange> parentChanges,
            List<ConnectionChange> subChanges) {
        List<ConnectionChange> combined = new ArrayList<>(parentChanges.size() + subChanges.size());
        combined.addAll(parentChanges);
        combined.addAll(subChanges);
        if (combined.isEmpty()) {
            return Collections.emptyList();
        }

        NodeConnection origin = combined.get(0).getChangedConnection();
        NodeConnection current = null;
        for (ConnectionChange change : combined) {
            current = ChangeTypeEnum.DELETE.equals(change.getChangeType()) ? null : change.getResultConnection();
        }

        if (origin == null && current == null) {
            return Collections.emptyList();
        }

        ConnectionChange net = new ConnectionChange();
        net.setChangedConnection(origin);
        net.setResultConnection(current);
        net.setChangeType(current == null ? ChangeTypeEnum.DELETE
                : origin == null ? ChangeTypeEnum.ADD : ChangeTypeEnum.EDIT);
        return List.of(net);
    }
}
