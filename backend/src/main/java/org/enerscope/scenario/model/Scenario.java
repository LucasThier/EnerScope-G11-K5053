package org.enerscope.scenario.model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.Objects;
import java.util.UUID;

/** Stable user-facing identity; the legacy version remains the editable workspace during migration. */
@Entity
@Table(name = "scenario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Scenario {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 320) private String name;
    @Column(nullable = false, length = 500) private String description = "";
    @Column(name = "base_scenario_id", updatable = false) private UUID baseScenarioId;
    @Column(name = "branch_revision_id", updatable = false) private UUID branchRevisionId;
    @Column(name = "current_revision_id") private UUID currentRevisionId;
    @Version private long generation;

    public Scenario(UUID id, UUID projectId, String name, UUID baseScenarioId, UUID branchRevisionId) {
        this.id = Objects.requireNonNull(id);
        this.projectId = Objects.requireNonNull(projectId);
        this.baseScenarioId = baseScenarioId;
        this.branchRevisionId = branchRevisionId;
        updateDetails(name, "");
    }

    public void updateDetails(String name, String description) {
        if (name == null || name.isBlank() || name.length() > 320)
            throw new IllegalArgumentException("Scenario name must contain 1 to 320 characters");
        if (description != null && description.length() > 500)
            throw new IllegalArgumentException("Scenario description exceeds 500 characters");
        this.name = name.trim();
        this.description = description == null ? "" : description;
    }

    public void publish(ScenarioRevision revision) {
        if (!id.equals(revision.getScenarioId()) || revision.getId() == null)
            throw new IllegalArgumentException("Revision must be persisted and belong to this scenario");
        if (!Objects.equals(currentRevisionId, revision.getParentRevisionId()))
            throw new IllegalStateException("Revision parent is no longer current");
        currentRevisionId = revision.getId();
    }
}
