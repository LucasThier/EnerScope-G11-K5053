package org.enerscope.comparison.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.version.model.Version;

/**
 * One evaluation inside a saved comparison. {@code position} is the order it was picked in.
 * The version is stored next to the evaluation so the comparison keeps pointing
 * at the scenario even when it is listed without loading the snapshot.
 */
@Entity
@Table(name = "scenario_comparison_item",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"comparison_id", "position"}),
                @UniqueConstraint(columnNames = {"comparison_id", "evaluation_id"})})
@Getter
@NoArgsConstructor
public class ScenarioComparisonItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "comparison_id", nullable = false, updatable = false)
    private ScenarioComparison comparison;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false, updatable = false)
    private Version version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evaluation_id", nullable = false, updatable = false)
    private EconomicEvaluation evaluation;

    @Column(nullable = false, updatable = false)
    private int position;

    /** Default scenario of the version (double write, see ScenarioDefaults); nullable until E7. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scenario_id")
    private org.enerscope.scenario.model.Scenario scenario;

    /** The version's name when the comparison was saved (the version may be renamed or deleted later). */
    @Column(name = "version_name", length = 320, updatable = false)
    private String versionName;

    /** The scenario's name when the comparison was saved (the scenario may be renamed or deleted later). */
    @Column(name = "scenario_name", length = 120, updatable = false)
    private String scenarioName;

    /** Records the scenario and its current name; call it while building the item, before it is saved. */
    public void assignScenario(org.enerscope.scenario.model.Scenario scenario) {
        this.scenario = scenario;
        if (scenario != null) this.scenarioName = scenario.getName();
    }

    public ScenarioComparisonItem(ScenarioComparison comparison, Version version, EconomicEvaluation evaluation,
                                  int position) {
        this.comparison = comparison;
        this.version = version;
        this.versionName = version.getName();
        this.evaluation = evaluation;
        this.position = position;
    }
}
