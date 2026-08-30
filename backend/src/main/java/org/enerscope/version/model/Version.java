package org.enerscope.version.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.Project;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "version")
public class Version extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "parent_version_id", nullable = true)
    private Version parentVersion;

    public Version(String name, Project project, Version parentVersion) {
        this.name = name;
        this.project = project;
        this.parentVersion = parentVersion;
    }
}
