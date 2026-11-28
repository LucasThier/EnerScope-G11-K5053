package org.enerscope.scenario.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

/** Editor drafts are separate from validated engine configurations. */
@Entity
@Table(name = "economic_draft")
@Getter
@NoArgsConstructor
public class EconomicDraftEntity extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private Version version;
    @Column(name = "draft_json", nullable = false, columnDefinition = "text")
    private String draftJson;
    @jakarta.persistence.Version
    private long revision;

    /** Default scenario of the version (double write, see ScenarioDefaults); nullable until E7. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id", unique = true)
    private org.enerscope.scenario.model.Scenario scenario;

    public void assignScenario(org.enerscope.scenario.model.Scenario scenario) {
        this.scenario = scenario;
    }

    public EconomicDraftEntity(Version version, String json) {
        this.version = version;
        this.draftJson = json;
    }

    public void replace(String json) {
        this.draftJson = json;
    }
}
