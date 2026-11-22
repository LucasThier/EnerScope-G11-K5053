package org.enerscope.economic.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

@Entity
@Table(name = "economic_evaluation")
@Getter
@NoArgsConstructor
public class EconomicEvaluation extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private Version version;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text", updatable = false)
    private String snapshotJson;
    /** Default scenario of the version (double write, see ScenarioDefaults); nullable until E7. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private org.enerscope.scenario.model.Scenario scenario;

    public void assignScenario(org.enerscope.scenario.model.Scenario scenario) {
        this.scenario = scenario;
    }

    public EconomicEvaluation(Version version, String snapshotJson) {
        this.version = version;
        this.snapshotJson = snapshotJson;
    }
}
