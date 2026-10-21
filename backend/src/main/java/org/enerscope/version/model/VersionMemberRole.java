package org.enerscope.version.model;

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
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;

import java.util.HashSet;
import java.util.Set;

/**
 * One role held by a {@link VersionMember}. Unlike {@code ProjectMemberRole},
 * a member can hold several of these at once (see {@link VersionMember}), and
 * an {@code EDITOR} role is scoped to a single {@link VerticalEnum} via
 * {@link #vertical} — {@code null} for {@code ADMIN}/{@code SIMULATOR}, which
 * apply to the whole version.
 */
@NoArgsConstructor
@Getter
@Entity
@Table(name = "version_member_role")
public class VersionMemberRole extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 30)
    private VersionMemberType memberType;

    @Enumerated(EnumType.STRING)
    @Column(name = "vertical", length = 30)
    private VerticalEnum vertical;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "version_member_role_permission",
            joinColumns = @JoinColumn(name = "version_member_role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<VersionMemberPermission> permissions = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "version_member_id", nullable = false)
    private VersionMember member;

    public VersionMemberRole(String name, VersionMemberType memberType,
                              Set<VersionMemberPermission> permissions, VerticalEnum vertical) {
        if (memberType == VersionMemberType.EDITOR && vertical == null) {
            throw new IllegalArgumentException("An EDITOR role requires a vertical");
        }
        if (memberType != VersionMemberType.EDITOR && vertical != null) {
            throw new IllegalArgumentException(memberType + " roles apply to the whole version, not one vertical");
        }
        this.name = name;
        this.memberType = memberType;
        this.permissions = permissions;
        this.vertical = vertical;
    }

    void assignToMember(VersionMember member) {
        this.member = member;
    }
}
