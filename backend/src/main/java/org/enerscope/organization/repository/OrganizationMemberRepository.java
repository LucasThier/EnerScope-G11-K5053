package org.enerscope.organization.repository;

import org.enerscope.organization.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    /**
     * Members of an organization with their user and roles already fetched, so
     * mapping to a DTO never triggers a lazy load per row.
     */
    @Query("""
            SELECT DISTINCT m FROM OrganizationMember m
            JOIN FETCH m.user
            LEFT JOIN FETCH m.roles
            WHERE m.organization.id = :organizationId
            ORDER BY m.createdAt
            """)
    List<OrganizationMember> findByOrganizationIdWithUser(@Param("organizationId") UUID organizationId);
}
