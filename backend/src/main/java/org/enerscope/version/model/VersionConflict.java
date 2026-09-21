package org.enerscope.version.model;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.enerscope.common.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Records that merging {@code sourceVersion} into {@code mergedVersion} touched
 * entities that a sibling ({@code conflictingVersion}) also changed. The merge
 * moment is {@code createdAt}.
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

    @ElementCollection
    @CollectionTable(name = "version_conflict_node", joinColumns = @JoinColumn(name = "conflict_id"))
    @Column(name = "node_id")
    private Set<UUID> conflictingNodeIds = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "version_conflict_connection", joinColumns = @JoinColumn(name = "conflict_id"))
    @Column(name = "connection_id")
    private Set<UUID> conflictingConnectionIds = new HashSet<>();

    @Column(nullable = false)
    private boolean resolved = false;

    @Column
    private Instant resolvedAt;

    public VersionConflict(Version mergedVersion, Version sourceVersion, Version conflictingVersion,
            Set<UUID> conflictingNodeIds, Set<UUID> conflictingConnectionIds) {
        this.mergedVersion = mergedVersion;
        this.sourceVersion = sourceVersion;
        this.conflictingVersion = conflictingVersion;
        this.conflictingNodeIds = new HashSet<>(conflictingNodeIds);
        this.conflictingConnectionIds = new HashSet<>(conflictingConnectionIds);
    }

    public void resolve() {
        this.resolved = true;
        this.resolvedAt = Instant.now();
    }
}
