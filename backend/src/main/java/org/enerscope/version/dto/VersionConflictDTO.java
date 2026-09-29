package org.enerscope.version.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.enums.ChangeTypeEnum;
import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionConflict;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Everything a client needs to explain a conflict without a second round
 * trip: not just which version/identity, but what each side currently has
 * for it ({@code mergedDescription}/{@code conflictingDescription}) and what
 * kind of change (ADD/EDIT/DELETE) each side's own history records for it
 * ({@code sourceChangeType}/{@code conflictingChangeType} - read straight off
 * {@code NodeChange}/{@code ConnectionChange}, per identity).
 * {@code conflictingChangeType} is null when the sibling never explicitly
 * touched this identity at all - it's simply stale relative to the merge.
 */
@Getter
@AllArgsConstructor
public class VersionConflictDTO {
    private final UUID id;
    private final UUID mergedVersionId;
    private final UUID sourceVersionId;
    private final UUID conflictingVersionId;
    private final String conflictingVersionName;
    private final ConflictEntityType entityType;
    private final UUID identityId;
    /** What the merged parent currently has for this identity, e.g. {@code WELL "nodito5"}; null if it has none. */
    private final String mergedDescription;
    /** What the conflicting sibling currently has for this identity; null if it has none. */
    private final String conflictingDescription;
    /** What the merged source itself did to this identity (from its own change history). */
    private final ChangeTypeEnum sourceChangeType;
    /** What the sibling itself did to this identity; null if the sibling never explicitly touched it. */
    private final ChangeTypeEnum conflictingChangeType;
    private final Instant mergedAt;
    private final boolean resolved;
    private final Instant resolvedAt;

    public static VersionConflictDTO from(VersionConflict conflict) {
        Version merged = conflict.getMergedVersion();
        Version source = conflict.getSourceVersion();
        Version conflicting = conflict.getConflictingVersion();
        UUID identityId = conflict.getIdentityId();

        String mergedDescription;
        String conflictingDescription;
        ChangeTypeEnum sourceChangeType;
        ChangeTypeEnum conflictingChangeType;
        if (conflict.getEntityType() == ConflictEntityType.NODE) {
            mergedDescription = describeNode(findNodeByIdentity(merged.getNodeSnapshot(), identityId));
            conflictingDescription = describeNode(findNodeByIdentity(conflicting.getNodeSnapshot(), identityId));
            sourceChangeType = findNodeChangeType(source.getNodeChanges(), identityId);
            conflictingChangeType = findNodeChangeType(conflicting.getNodeChanges(), identityId);
        } else {
            mergedDescription = describeConnection(
                    findConnectionByIdentity(merged.getConnectionSnapshot(), identityId), merged);
            conflictingDescription = describeConnection(
                    findConnectionByIdentity(conflicting.getConnectionSnapshot(), identityId), conflicting);
            sourceChangeType = findConnectionChangeType(source.getConnectionChanges(), identityId);
            conflictingChangeType = findConnectionChangeType(conflicting.getConnectionChanges(), identityId);
        }

        return new VersionConflictDTO(
                conflict.getId(),
                merged.getId(),
                source.getId(),
                conflicting.getId(),
                conflicting.getName(),
                conflict.getEntityType(),
                identityId,
                mergedDescription,
                conflictingDescription,
                sourceChangeType,
                conflictingChangeType,
                conflict.getCreatedAt(),
                conflict.isResolved(),
                conflict.getResolvedAt());
    }

    private static BaseNode findNodeByIdentity(List<BaseNode> nodes, UUID identityId) {
        if (nodes == null) {
            return null;
        }
        return nodes.stream()
                .filter(node -> node != null && identityId.equals(node.getIdentityId()))
                .findFirst()
                .orElse(null);
    }

    private static NodeConnection findConnectionByIdentity(List<NodeConnection> connections, UUID identityId) {
        if (connections == null) {
            return null;
        }
        return connections.stream()
                .filter(connection -> connection != null && identityId.equals(connection.getIdentityId()))
                .findFirst()
                .orElse(null);
    }

    /** The identity's own change type in {@code changes}, or null if it isn't mentioned there at all. */
    private static ChangeTypeEnum findNodeChangeType(List<NodeChange> changes, UUID identityId) {
        if (changes == null) {
            return null;
        }
        return changes.stream()
                .filter(change -> matchesIdentity(change.getChangedNode(), identityId)
                        || matchesIdentity(change.getResultNode(), identityId))
                .map(NodeChange::getChangeType)
                .findFirst()
                .orElse(null);
    }

    private static ChangeTypeEnum findConnectionChangeType(List<ConnectionChange> changes, UUID identityId) {
        if (changes == null) {
            return null;
        }
        return changes.stream()
                .filter(change -> matchesIdentity(change.getChangedConnection(), identityId)
                        || matchesIdentity(change.getResultConnection(), identityId))
                .map(ConnectionChange::getChangeType)
                .findFirst()
                .orElse(null);
    }

    private static boolean matchesIdentity(BaseNode node, UUID identityId) {
        return node != null && identityId.equals(node.getIdentityId());
    }

    private static boolean matchesIdentity(NodeConnection connection, UUID identityId) {
        return connection != null && identityId.equals(connection.getIdentityId());
    }

    private static String describeNode(BaseNode node) {
        if (node == null) {
            return null;
        }
        String type = node.getType() != null && node.getType().getNodeType() != null
                ? node.getType().getNodeType().toString()
                : "NODE";
        return type + " \"" + node.getName() + "\"";
    }

    private static String describeConnection(NodeConnection connection, Version owner) {
        if (connection == null) {
            return null;
        }
        return describeEndpoint(connection.getFromNodeId(), owner) + " → "
                + describeEndpoint(connection.getToNodeId(), owner);
    }

    private static String describeEndpoint(UUID nodeId, Version owner) {
        if (nodeId == null) {
            return "?";
        }
        List<BaseNode> nodes = owner.getNodeSnapshot();
        if (nodes != null) {
            for (BaseNode node : nodes) {
                if (node != null && nodeId.equals(node.getId())) {
                    return node.getName();
                }
            }
        }
        return nodeId.toString();
    }
}
