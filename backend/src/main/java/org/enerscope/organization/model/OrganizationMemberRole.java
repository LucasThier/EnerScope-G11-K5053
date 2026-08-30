package org.enerscope.organization.model;

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
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;

import java.util.HashSet;
import java.util.Set;

@NoArgsConstructor
@Getter
@Entity
@Table(name = "organization_member_role")
public class OrganizationMemberRole extends BaseEntity {

    @Column(nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "member_type", nullable = false, length = 30)
    private OrganizationMemberType memberType;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "organization_member_role_permission",
            joinColumns = @JoinColumn(name = "organization_member_role_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false, length = 40)
    private Set<OrganizationMemberPermission> permissions = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_member_id", nullable = false)
    private OrganizationMember member;

    public OrganizationMemberRole(String name, OrganizationMemberType memberType,
                                   Set<OrganizationMemberPermission> permissions) {
        this.name = name;
        this.memberType = memberType;
        this.permissions = permissions;
    }

    void assignToMember(OrganizationMember member) {
        this.member = member;
    }
}
