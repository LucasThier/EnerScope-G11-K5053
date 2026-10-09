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
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
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

    private static final Map<ProjectMemberType, Set<ProjectMemberPermission>> DEFAULT_PERMISSIONS = Map.of(
            ProjectMemberType.ADMIN, EnumSet.of(
                    ProjectMemberPermission.MANAGE_PROJECT,
                    ProjectMemberPermission.EDIT_PROJECT,
                    ProjectMemberPermission.VIEW_PROJECT),
            ProjectMemberType.EDITOR, EnumSet.of(
                    ProjectMemberPermission.EDIT_PROJECT,
                    ProjectMemberPermission.VIEW_PROJECT));

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

    public void changeRole(ProjectMemberType type, Set<ProjectMemberPermission> permissions) {
        roles.clear();
        addRole(new ProjectMemberRole(type.name(), type, new HashSet<>(permissions)));
    }

    public static Set<ProjectMemberPermission> defaultPermissionsFor(ProjectMemberType type) {
        return DEFAULT_PERMISSIONS.get(type);
    }
}
