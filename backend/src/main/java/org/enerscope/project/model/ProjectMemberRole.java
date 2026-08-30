package org.enerscope.project.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "project_member_role")
public class ProjectMemberRole extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 30)
    private ProjectMemberType memberType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "project_member_role_permission",
            joinColumns = @JoinColumn(name = "project_member_role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<ProjectMemberPermission> permissions = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_member_id", nullable = false)
    private ProjectMember member;

    public ProjectMemberRole(String name, ProjectMemberType memberType,
                              Set<ProjectMemberPermission> permissions) {
        this.name = name;
        this.memberType = memberType;
        this.permissions = permissions;
    }

    void assignToMember(ProjectMember member) {
        this.member = member;
    }
}
