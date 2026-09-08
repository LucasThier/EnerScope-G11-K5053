package org.enerscope.organization.model;

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
        name = "organization_member",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_org_member_org_user",
                columnNames = {"organization_id", "user_id"})
)
public class OrganizationMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<OrganizationMemberRole> roles = new HashSet<>();

    public OrganizationMember(User user, Organization organization) {
        this.user = user;
        this.organization = organization;
    }

    public void addRole(OrganizationMemberRole role) {
        roles.add(role);
        role.assignToMember(this);
    }
}
