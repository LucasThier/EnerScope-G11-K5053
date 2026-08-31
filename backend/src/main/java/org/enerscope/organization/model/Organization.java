package org.enerscope.organization.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.Project;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "organization")
public class Organization extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrganizationMember> members = new ArrayList<>();

    @OneToMany(mappedBy = "organization", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Project> projects = new ArrayList<>();

    public Organization(String name) {
        this.name = name;
    }

    public void addMember(OrganizationMember member) {
        members.add(member);
    }

    public void addProject(Project project) {
        projects.add(project);
    }
}
