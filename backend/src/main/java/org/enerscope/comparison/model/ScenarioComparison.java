package org.enerscope.comparison.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.Project;

@Entity
@Table(name = "scenario_comparison")
@Getter
@NoArgsConstructor
public class ScenarioComparison extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;
    @Column(nullable = false, length = 500, updatable = false)
    private String name;
    @Column(name = "configuration_json", nullable = false, columnDefinition = "text", updatable = false)
    private String configurationJson;
    @Column(name = "result_json", nullable = false, columnDefinition = "text", updatable = false)
    private String resultJson;

    public ScenarioComparison(Project project, String name, String configurationJson, String resultJson) {
        this.project = project;
        this.name = name;
        this.configurationJson = configurationJson;
        this.resultJson = resultJson;
    }
}
