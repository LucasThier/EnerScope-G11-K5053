package org.enerscope.comparison.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.version.model.Version;

/**
 * One evaluation inside a saved comparison. {@code position} 0 is the baseline.
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

    public ScenarioComparisonItem(ScenarioComparison comparison, Version version, EconomicEvaluation evaluation,
                                  int position) {
        this.comparison = comparison;
        this.version = version;
        this.evaluation = evaluation;
        this.position = position;
    }
}
