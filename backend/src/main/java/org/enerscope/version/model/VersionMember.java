package org.enerscope.version.model;

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
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A user's access to one version. Unlike {@code ProjectMember}, which holds
 * exactly one role at a time, a {@code VersionMember} may hold any number of
 * {@link VersionMemberRole}s at once — e.g. EDITOR for several verticals plus
 * SIMULATOR — since roles never get cleared, only added or removed one at a
 * time (see {@link #addRole}/{@link #removeRole}).
 */
@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "version_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_version_member_version_user",
                columnNames = {"version_id", "user_id"})
)
public class VersionMember extends BaseEntity {

    private static final Map<VersionMemberType, Set<VersionMemberPermission>> DEFAULT_PERMISSIONS = Map.of(
            VersionMemberType.ADMIN, EnumSet.of(
                    VersionMemberPermission.MANAGE_VERSION,
                    VersionMemberPermission.EDIT_VERSION,
                    VersionMemberPermission.SIMULATE_VERSION,
                    VersionMemberPermission.VIEW_VERSION),
            VersionMemberType.EDITOR, EnumSet.of(
                    VersionMemberPermission.EDIT_VERSION,
                    VersionMemberPermission.VIEW_VERSION),
            VersionMemberType.SIMULATOR, EnumSet.of(
                    VersionMemberPermission.SIMULATE_VERSION,
                    VersionMemberPermission.VIEW_VERSION));

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_id", nullable = false)
    private Version version;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<VersionMemberRole> roles = new HashSet<>();

    public VersionMember(User user, Version version) {
        this.user = user;
        this.version = version;
    }

    public void addRole(VersionMemberRole role) {
        roles.add(role);
        role.assignToMember(this);
    }

    public void removeRole(VersionMemberRole role) {
        roles.remove(role);
    }

    public static Set<VersionMemberPermission> defaultPermissionsFor(VersionMemberType type) {
        return DEFAULT_PERMISSIONS.get(type);
    }
}
