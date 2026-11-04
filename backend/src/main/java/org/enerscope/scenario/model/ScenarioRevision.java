package org.enerscope.scenario.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.hibernate.annotations.Immutable;
import java.util.Objects;
import java.util.UUID;

/** Immutable complete input. Execution parameters belong to the simulation. */
@Entity
@Table(name = "scenario_revision", uniqueConstraints = @UniqueConstraint(columnNames = {"scenario_id", "number"}))
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScenarioRevision extends BaseEntity {
    @Column(name = "scenario_id", nullable = false, updatable = false)
    private UUID scenarioId;
    @Column(nullable = false, updatable = false)
    private int number;
    @Column(name = "created_by", updatable = false)
    private UUID createdBy;
    @Column(name = "parent_revision_id", updatable = false)
    private UUID parentRevisionId;
    @Column(name = "merge_source_revision_id", updatable = false)
    private UUID mergeSourceRevisionId;
    @Column(name = "merge_base_revision_id", updatable = false)
    private UUID mergeBaseRevisionId;
    @Column(name = "snapshot_json", nullable = false, updatable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "content_hash", nullable = false, updatable = false, length = 64)
    private String contentHash;

    public ScenarioRevision(UUID scenarioId, int number, UUID author, UUID parent,
                            UUID mergeSource, UUID mergeBase, String snapshot, String hash) {
        if (number < 1) throw new IllegalArgumentException("Revision number must be positive");
        if (snapshot == null || snapshot.isBlank()) throw new IllegalArgumentException("Revision snapshot is required");
        if (hash == null || !hash.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid content hash");
        if (mergeBase != null && mergeSource == null) throw new IllegalArgumentException("Merge base requires a source");
        this.scenarioId = Objects.requireNonNull(scenarioId);
        this.number = number;
        this.createdBy = author;
        this.parentRevisionId = parent;
        this.mergeSourceRevisionId = mergeSource;
        this.mergeBaseRevisionId = mergeBase;
        this.snapshotJson = snapshot;
        this.contentHash = hash;
    }

    @Override public void deactivate() { throw new UnsupportedOperationException("Revisions are immutable"); }
    @Override public void activate() { throw new UnsupportedOperationException("Revisions are immutable"); }
}
