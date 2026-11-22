package org.enerscope.scenario.model;
import jakarta.persistence.*;
import lombok.*;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

@Entity
@Table(name="scenario_draft")
@Getter
@NoArgsConstructor
public class ScenarioDraft extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="version_id",nullable=false)
    private Version version;
    @Column(name="draft_json",nullable=false,columnDefinition="text")
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

    public ScenarioDraft(Version version,String json) {
        this.version = version;
        this.draftJson = json;
    }

    public void replace(String json) {
        draftJson = json;
    }
}
