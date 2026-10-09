package org.enerscope.version.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.enerscope.common.BaseEntity;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.node.repository.BaseNodeRepository;
import org.enerscope.node.repository.NodeConnectionRepository;
import org.enerscope.node.service.NodeCloner;
import org.enerscope.version.dto.ConflictResolutionType;
import org.enerscope.version.dto.VersionConflictDTO;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;
import org.enerscope.version.repository.VersionConflictRepository;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class VersionConflictService {

    private final VersionRepository versionRepository;
    private final VersionConflictRepository conflictRepository;
    private final BaseNodeRepository nodeRepository;
    private final NodeConnectionRepository connectionRepository;
    private final AppLogger logger;

    /**
     * Called right after {@code sourceVersion} was merged into
     * {@code mergedVersion}. For every identity the merged source touched, and
     * every other child of {@code mergedVersion}, compares that sibling's
     * current node/connection for the identity against what the merge just
     * left in {@code mergedVersion}. Any mismatch is a conflict - including
     * when the sibling never explicitly changed that identity itself: its
     * snapshot is a static copy taken when it branched off, so it can simply
     * be stale relative to what its parent now holds after this merge. If
     * that staleness went unflagged, the sibling's own eventual merge would
     * silently overwrite/discard what this merge just did (see
     * {@code VersionService#mergeSubVersionIntoParent}, which applies each
     * side's changes as a diff for exactly this reason).
     */
    @Transactional
    public List<VersionConflict> recordMergeConflicts(Version mergedVersion, Version sourceVersion) {
        Objects.requireNonNull(mergedVersion, "Merged version cannot be null");
        Objects.requireNonNull(sourceVersion, "Source version cannot be null");

        Set<UUID> sourceNodeIdentities = changedNodeIdentities(sourceVersion);
        Set<UUID> sourceConnectionIdentities = changedConnectionIdentities(sourceVersion);
        List<VersionConflict> created = new ArrayList<>();
        if (sourceNodeIdentities.isEmpty() && sourceConnectionIdentities.isEmpty()) {
            return created;
        }

        for (Version sibling : versionRepository.findByParentVersionId(mergedVersion.getId())) {
            if (Objects.equals(sibling.getId(), sourceVersion.getId())) {
                continue;
            }

            for (UUID identityId : sourceNodeIdentities) {
                BaseNode mergedNode = findNodeByIdentity(mergedVersion.getNodeSnapshot(), identityId);
                BaseNode siblingNode = findNodeByIdentity(sibling.getNodeSnapshot(), identityId);
                if (rowDiffers(mergedNode, siblingNode)) {
                    created.add(conflictRepository.save(new VersionConflict(mergedVersion, sourceVersion, sibling,
                            ConflictEntityType.NODE, identityId)));
                }
            }

            for (UUID identityId : sourceConnectionIdentities) {
                NodeConnection mergedConnection = findConnectionByIdentity(mergedVersion.getConnectionSnapshot(),
                        identityId);
                NodeConnection siblingConnection = findConnectionByIdentity(sibling.getConnectionSnapshot(),
                        identityId);
                if (rowDiffers(mergedConnection, siblingConnection)) {
                    created.add(conflictRepository.save(new VersionConflict(mergedVersion, sourceVersion, sibling,
                            ConflictEntityType.CONNECTION, identityId)));
                }
            }
        }

        if (!created.isEmpty()) {
            logger.info("Merge of {} into {} created {} conflict(s) with sibling versions",
                    sourceVersion.getName(), mergedVersion.getName(), created.size());
        }
        return created;
    }

    /** True when the two sides disagree on this identity: one is missing it, or they hold different rows. */
    private boolean rowDiffers(BaseEntity mergedSide, BaseEntity siblingSide) {
        if (mergedSide == null && siblingSide == null) {
            return false;
        }
        if (mergedSide == null || siblingSide == null) {
            return true;
        }
        return !Objects.equals(mergedSide.getId(), siblingSide.getId());
    }

    /** Conflicts that affect the given version (it is the conflicting sibling). */
    public List<VersionConflictDTO> getConflictsForVersion(UUID versionId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        if (!versionRepository.existsById(versionId)) {
            throw new VersionNotFoundException(versionId);
        }
        return conflictRepository.findByConflictingVersionId(versionId).stream()
                .map(VersionConflictDTO::from)
                .toList();
    }

    /** Every recorded conflict, regardless of version — for testing/debugging. */
    public List<VersionConflictDTO> getAllConflicts() {
        return conflictRepository.findAll().stream()
                .map(VersionConflictDTO::from)
                .toList();
    }

    /**
     * Resolves a single conflicting entity for the sibling version, either
     * adopting the merged parent's data ({@link ConflictResolutionType#ACCEPT})
     * or keeping the sibling's own data while recording its divergence from
     * the (now-merged) parent ({@link ConflictResolutionType#REJECT}).
     */
    @Transactional
    public VersionConflictDTO resolveConflict(UUID conflictId, ConflictResolutionType decision) {
        Objects.requireNonNull(conflictId, "Conflict ID cannot be null");
        Objects.requireNonNull(decision, "Resolution decision cannot be null");
        VersionConflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> new EntityNotFoundException("Version conflict not found with id: " + conflictId));

        if (conflict.getEntityType() == ConflictEntityType.NODE) {
            resolveNodeConflict(conflict, decision);
        } else {
            resolveConnectionConflict(conflict, decision);
        }

        conflict.resolve();
        VersionConflict saved = conflictRepository.save(conflict);
        logger.info("Resolved version conflict {} ({})", conflictId, decision);
        return VersionConflictDTO.from(saved);
    }

    // ---------------------------------------------------------------------
    // Node resolution
    // ---------------------------------------------------------------------

    private void resolveNodeConflict(VersionConflict conflict, ConflictResolutionType decision) {
        UUID identityId = conflict.getIdentityId();
        Version parent = conflict.getMergedVersion();
        Version sibling = conflict.getConflictingVersion();

        BaseNode parentNode = findNodeByIdentity(parent.getNodeSnapshot(), identityId);
        BaseNode siblingNode = findNodeByIdentity(sibling.getNodeSnapshot(), identityId);

        removeNodeChangesForIdentity(sibling, identityId);

        if (decision == ConflictResolutionType.ACCEPT) {
            if (parentNode == null) {
                if (siblingNode != null) {
                    sibling.getNodeSnapshot().remove(siblingNode);
                }
            } else if (siblingNode != null) {
                NodeCloner.copyNodeData(siblingNode, parentNode);
                nodeRepository.save(siblingNode);
            } else {
                BaseNode clone = NodeCloner.cloneNode(parentNode);
                nodeRepository.save(clone);
                sibling.getNodeSnapshot().add(clone);
            }
        } else {
            NodeChange change = new NodeChange();
            if (parentNode != null && siblingNode != null) {
                change.setChangeType(ChangeTypeEnum.EDIT);
                change.setChangedNode(parentNode);
                change.setResultNode(siblingNode);
            } else if (parentNode != null) {
                change.setChangeType(ChangeTypeEnum.DELETE);
                change.setChangedNode(parentNode);
            } else if (siblingNode != null) {
                change.setChangeType(ChangeTypeEnum.ADD);
                change.setResultNode(siblingNode);
            } else {
                return;
            }
            sibling.getNodeChanges().add(change);
        }

        versionRepository.save(sibling);
    }

    private BaseNode findNodeByIdentity(List<BaseNode> nodes, UUID identityId) {
        if (nodes == null) {
            return null;
        }
        for (BaseNode node : nodes) {
            if (node != null && identityId.equals(node.getIdentityId())) {
                return node;
            }
        }
        return null;
    }

    private void removeNodeChangesForIdentity(Version version, UUID identityId) {
        if (version.getNodeChanges() == null) {
            return;
        }
        version.getNodeChanges().removeIf(change -> matchesIdentity(change.getChangedNode(), identityId)
                || matchesIdentity(change.getResultNode(), identityId));
    }

    private boolean matchesIdentity(BaseNode node, UUID identityId) {
        return node != null && identityId.equals(node.getIdentityId());
    }

    // ---------------------------------------------------------------------
    // Connection resolution
    // ---------------------------------------------------------------------

    private void resolveConnectionConflict(VersionConflict conflict, ConflictResolutionType decision) {
        UUID identityId = conflict.getIdentityId();
        Version parent = conflict.getMergedVersion();
        Version sibling = conflict.getConflictingVersion();

        NodeConnection parentConnection = findConnectionByIdentity(parent.getConnectionSnapshot(), identityId);
        NodeConnection siblingConnection = findConnectionByIdentity(sibling.getConnectionSnapshot(), identityId);

        removeConnectionChangesForIdentity(sibling, identityId);

        if (decision == ConflictResolutionType.ACCEPT) {
            if (parentConnection == null) {
                if (siblingConnection != null) {
                    sibling.getConnectionSnapshot().remove(siblingConnection);
                }
            } else if (siblingConnection != null) {
                copyConnectionData(siblingConnection, parentConnection, parent, sibling);
                connectionRepository.save(siblingConnection);
            } else {
                NodeConnection clone = new NodeConnection();
                clone.setIdentityId(identityId);
                copyConnectionData(clone, parentConnection, parent, sibling);
                connectionRepository.save(clone);
                sibling.getConnectionSnapshot().add(clone);
            }
        } else {
            ConnectionChange change = new ConnectionChange();
            if (parentConnection != null && siblingConnection != null) {
                change.setChangeType(ChangeTypeEnum.EDIT);
                change.setChangedConnection(parentConnection);
                change.setResultConnection(siblingConnection);
            } else if (parentConnection != null) {
                change.setChangeType(ChangeTypeEnum.DELETE);
                change.setChangedConnection(parentConnection);
            } else if (siblingConnection != null) {
                change.setChangeType(ChangeTypeEnum.ADD);
                change.setResultConnection(siblingConnection);
            } else {
                return;
            }
            sibling.getConnectionChanges().add(change);
        }

        versionRepository.save(sibling);
    }

    private NodeConnection findConnectionByIdentity(List<NodeConnection> connections, UUID identityId) {
        if (connections == null) {
            return null;
        }
        for (NodeConnection connection : connections) {
            if (connection != null && identityId.equals(connection.getIdentityId())) {
                return connection;
            }
        }
        return null;
    }

    private void removeConnectionChangesForIdentity(Version version, UUID identityId) {
        if (version.getConnectionChanges() == null) {
            return;
        }
        version.getConnectionChanges().removeIf(change -> matchesIdentity(change.getChangedConnection(), identityId)
                || matchesIdentity(change.getResultConnection(), identityId));
    }

    private boolean matchesIdentity(NodeConnection connection, UUID identityId) {
        return connection != null && identityId.equals(connection.getIdentityId());
    }

    /**
     * Copies {@code source}'s from/to node ids onto {@code target}, remapped
     * through node identity: each endpoint's identity is looked up in
     * {@code parent}'s node snapshot, then matched against a node in
     * {@code sibling}'s own snapshot. Falls back to the parent's raw id when
     * the sibling has no node for that identity yet (its own conflict has not
     * been resolved).
     */
    private void copyConnectionData(NodeConnection target, NodeConnection source, Version parent, Version sibling) {
        target.setFromNodeId(remapEndpoint(source.getFromNodeId(), parent, sibling));
        target.setToNodeId(remapEndpoint(source.getToNodeId(), parent, sibling));
    }

    private UUID remapEndpoint(UUID parentNodeId, Version parent, Version sibling) {
        if (parentNodeId == null) {
            return null;
        }
        BaseNode parentNode = findNodeById(parent.getNodeSnapshot(), parentNodeId);
        if (parentNode == null) {
            return parentNodeId;
        }
        BaseNode siblingNode = findNodeByIdentity(sibling.getNodeSnapshot(), parentNode.getIdentityId());
        return siblingNode != null ? siblingNode.getId() : parentNodeId;
    }

    private BaseNode findNodeById(List<BaseNode> nodes, UUID id) {
        if (nodes == null) {
            return null;
        }
        for (BaseNode node : nodes) {
            if (node != null && id.equals(node.getId())) {
                return node;
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------
    // Detection helpers
    // ---------------------------------------------------------------------

    private Set<UUID> changedNodeIdentities(Version version) {
        Set<UUID> identities = new HashSet<>();
        if (version.getNodeChanges() == null) {
            return identities;
        }
        for (NodeChange change : version.getNodeChanges()) {
            addIdentity(identities, change.getChangedNode());
            addIdentity(identities, change.getResultNode());
        }
        return identities;
    }

    private Set<UUID> changedConnectionIdentities(Version version) {
        Set<UUID> identities = new HashSet<>();
        if (version.getConnectionChanges() == null) {
            return identities;
        }
        for (ConnectionChange change : version.getConnectionChanges()) {
            addIdentity(identities, change.getChangedConnection());
            addIdentity(identities, change.getResultConnection());
        }
        return identities;
    }

    private void addIdentity(Set<UUID> identities, BaseNode node) {
        if (node != null && node.getIdentityId() != null) {
            identities.add(node.getIdentityId());
        }
    }

    private void addIdentity(Set<UUID> identities, NodeConnection connection) {
        if (connection != null && connection.getIdentityId() != null) {
            identities.add(connection.getIdentityId());
        }
    }
}
