package org.enerscope.project.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.user.model.User;

import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "project_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_project_member_project_user",
                columnNames = {"project_id", "user_id"})
)
public class ProjectMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ProjectMemberRole> roles = new HashSet<>();

    public ProjectMember(User user, Project project) {
        this.user = user;
        this.project = project;
    }

    public void addRole(ProjectMemberRole role) {
        roles.add(role);
        role.assignToMember(this);
    }
}
