package org.enerscope.version.dto;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.enerscope.version.model.VersionConflict;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class VersionConflictDTO {
    private final UUID id;
    private final UUID mergedVersionId;
    private final UUID sourceVersionId;
    private final UUID conflictingVersionId;
    private final String conflictingVersionName;
    private final Set<UUID> conflictingNodeIds;
    private final Set<UUID> conflictingConnectionIds;
    private final Instant mergedAt;
    private final boolean resolved;
    private final Instant resolvedAt;

    public static VersionConflictDTO from(VersionConflict conflict) {
        return new VersionConflictDTO(
                conflict.getId(),
                conflict.getMergedVersion().getId(),
                conflict.getSourceVersion().getId(),
                conflict.getConflictingVersion().getId(),
                conflict.getConflictingVersion().getName(),
                Set.copyOf(conflict.getConflictingNodeIds()),
                Set.copyOf(conflict.getConflictingConnectionIds()),
                conflict.getCreatedAt(),
                conflict.isResolved(),
                conflict.getResolvedAt());
    }
}
