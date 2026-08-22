package org.enerscope.organization.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "project")
public class Project extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    public Project(String name, String description, Organization organization) {
        this.name = name;
        this.description = description;
        this.organization = organization;
    }
}
