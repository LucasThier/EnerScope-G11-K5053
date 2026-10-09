package org.enerscope.organization.repository;

import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.project.dto.ProjectMemberCandidateDTO;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {
    boolean existsByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Optional<OrganizationMember> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByOrganizationId(UUID organizationId);

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

    @Query("""
            SELECT new org.enerscope.project.dto.ProjectMemberCandidateDTO(
                u.id, u.firstName, u.lastName, u.mail)
            FROM OrganizationMember om
            JOIN om.user u
            WHERE om.organization.id = :organizationId
              AND om.active = true
              AND u.active = true
              AND NOT EXISTS (
                  SELECT 1 FROM ProjectMember pm
                  WHERE pm.project.id = :projectId AND pm.user = u)
              AND (LOWER(u.firstName) LIKE :pattern ESCAPE '\\'
                   OR LOWER(u.lastName) LIKE :pattern ESCAPE '\\'
                   OR LOWER(u.mail) LIKE :pattern ESCAPE '\\')
            ORDER BY u.lastName, u.firstName
            """)
    List<ProjectMemberCandidateDTO> findProjectMemberCandidates(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId,
            @Param("pattern") String pattern);
}
