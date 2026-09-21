package org.enerscope.version.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.ConnectionChange;
import org.enerscope.node.model.NodeChange;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.version.dto.VersionConflictDTO;
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
    private final AppLogger logger;

    /**
     * Called right after {@code sourceVersion} was merged into
     * {@code mergedVersion}. Creates one {@link VersionConflict} for every other
     * child of {@code mergedVersion} that changed a node or connection also
     * changed by the merged source.
     */
    @Transactional
    public List<VersionConflict> recordMergeConflicts(Version mergedVersion, Version sourceVersion) {
        Objects.requireNonNull(mergedVersion, "Merged version cannot be null");
        Objects.requireNonNull(sourceVersion, "Source version cannot be null");

        Set<UUID> sourceNodeIds = changedNodeIds(sourceVersion);
        Set<UUID> sourceConnectionIds = changedConnectionIds(sourceVersion);
        List<VersionConflict> created = new ArrayList<>();
        if (sourceNodeIds.isEmpty() && sourceConnectionIds.isEmpty()) {
            return created;
        }

        for (Version sibling : versionRepository.findByParentVersionId(mergedVersion.getId())) {
            if (Objects.equals(sibling.getId(), sourceVersion.getId())) {
                continue;
            }

            Set<UUID> nodeIds = new HashSet<>(changedNodeIds(sibling));
            nodeIds.retainAll(sourceNodeIds);
            Set<UUID> connectionIds = new HashSet<>(changedConnectionIds(sibling));
            connectionIds.retainAll(sourceConnectionIds);

            if (nodeIds.isEmpty() && connectionIds.isEmpty()) {
                continue;
            }
            created.add(conflictRepository.save(
                    new VersionConflict(mergedVersion, sourceVersion, sibling, nodeIds, connectionIds)));
        }

        if (!created.isEmpty()) {
            logger.info("Merge of {} into {} created {} conflict(s) with sibling versions",
                    sourceVersion.getName(), mergedVersion.getName(), created.size());
        }
        return created;
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

    @Transactional
    public VersionConflictDTO resolveConflict(UUID conflictId) {
        Objects.requireNonNull(conflictId, "Conflict ID cannot be null");
        VersionConflict conflict = conflictRepository.findById(conflictId)
                .orElseThrow(() -> new EntityNotFoundException("Version conflict not found with id: " + conflictId));
        conflict.resolve();
        VersionConflict saved = conflictRepository.save(conflict);
        logger.info("Resolved version conflict {}", conflictId);
        return VersionConflictDTO.from(saved);
    }

    private Set<UUID> changedNodeIds(Version version) {
        Set<UUID> ids = new HashSet<>();
        if (version.getNodeChanges() == null) {
            return ids;
        }
        for (NodeChange change : version.getNodeChanges()) {
            addId(ids, change.getChangedNode());
            addId(ids, change.getResultNode());
        }
        return ids;
    }

    private Set<UUID> changedConnectionIds(Version version) {
        Set<UUID> ids = new HashSet<>();
        if (version.getConnectionChanges() == null) {
            return ids;
        }
        for (ConnectionChange change : version.getConnectionChanges()) {
            addId(ids, change.getChangedConnection());
            addId(ids, change.getResultConnection());
        }
        return ids;
    }

    private void addId(Set<UUID> ids, BaseNode node) {
        if (node != null && node.getId() != null) {
            ids.add(node.getId());
        }
    }

    private void addId(Set<UUID> ids, NodeConnection connection) {
        if (connection != null && connection.getId() != null) {
            ids.add(connection.getId());
        }
    }
}
