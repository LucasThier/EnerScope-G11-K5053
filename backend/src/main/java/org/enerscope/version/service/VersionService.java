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
        List<BaseNode> nodeSnapshot = null; // do we want a new version with new changes? Or not?
        List<NodeConnection> connectionSnapshot = null;
        if (data.getParentVersion() != null) {
            parentVersion = versionRepository.findById(data.getParentVersion())
                    .orElseThrow(() -> new VersionNotFoundException(data.getParentVersion()));
            // Create defensive copies to avoid sharing references with parent version
            connectionSnapshot = parentVersion.getConnectionSnapshot() == null
                    ? null
                    : new ArrayList<>(parentVersion.getConnectionSnapshot());
            nodeSnapshot = parentVersion.getNodeSnapshot() == null
                    ? null
                    : new ArrayList<>(parentVersion.getNodeSnapshot());
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
