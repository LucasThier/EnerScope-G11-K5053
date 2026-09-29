package org.enerscope.version.model;

import java.time.Instant;
import java.util.UUID;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records that merging {@code sourceVersion} into {@code mergedVersion} touched
 * a node/connection (identified by {@code identityId}, stable across edits)
 * that a sibling ({@code conflictingVersion}) also changed. The merge moment
 * is {@code createdAt}. One row per conflicting entity, so each can be
 * accepted or rejected independently.
 */
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "version_conflict")
public class VersionConflict extends BaseEntity {

    @ManyToOne(optional = false)
    @JoinColumn(name = "merged_version_id", nullable = false)
    private Version mergedVersion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "source_version_id", nullable = false)
    private Version sourceVersion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conflicting_version_id", nullable = false)
    private Version conflictingVersion;

    @Column(name = "entity_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private ConflictEntityType entityType;

    @Column(name = "identity_id", nullable = false)
    private UUID identityId;

    @Column(nullable = false)
    private boolean resolved = false;

    @Column
    private Instant resolvedAt;

    public VersionConflict(Version mergedVersion, Version sourceVersion, Version conflictingVersion,
            ConflictEntityType entityType, UUID identityId) {
        this.mergedVersion = mergedVersion;
        this.sourceVersion = sourceVersion;
        this.conflictingVersion = conflictingVersion;
        this.entityType = entityType;
        this.identityId = identityId;
    }

    public void resolve() {
        this.resolved = true;
        this.resolvedAt = Instant.now();
    }
}
