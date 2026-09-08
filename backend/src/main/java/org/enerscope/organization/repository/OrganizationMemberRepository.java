package org.enerscope.organization.repository;

import org.enerscope.organization.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);
}
