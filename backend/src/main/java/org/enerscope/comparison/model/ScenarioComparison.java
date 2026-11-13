package org.enerscope.comparison.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.Project;

/**
 * A saved comparison of economic evaluations of one project. Only the definition
 * is stored: which evaluations, in which order (the first is the baseline) and the
 * relevance thresholds. Evaluations are immutable, so the result is recalculated
 * on every read and always matches what was saved. Deleting is a soft delete.
 */
@Entity
@Table(name = "scenario_comparison")
@Getter
@NoArgsConstructor
public class ScenarioComparison extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** Relative change (fraction) from which a monetary or physical difference is relevant. */
    @Column(name = "relevance_threshold", nullable = false, precision = 9, scale = 6)
    private BigDecimal relevanceThreshold;

    /** Absolute change (fraction points) from which a rate difference is relevant. */
    @Column(name = "rate_threshold", nullable = false, precision = 9, scale = 6)
    private BigDecimal rateThreshold;

    @OneToMany(mappedBy = "comparison", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ScenarioComparisonItem> items = new ArrayList<>();

    public ScenarioComparison(Project project, String name, String description,
                              BigDecimal relevanceThreshold, BigDecimal rateThreshold) {
        this.project = project;
        this.name = name;
        this.description = description;
        this.relevanceThreshold = relevanceThreshold;
        this.rateThreshold = rateThreshold;
    }

    public void addItem(ScenarioComparisonItem item) {
        items.add(item);
    }
}
