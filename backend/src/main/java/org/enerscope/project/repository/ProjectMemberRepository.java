package org.enerscope.project.repository;

import org.enerscope.project.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

    /**
     * The caller's membership row, roles included, for permission checks.
     * {@code existsByProjectIdAndUserId} answers whether someone is on the
     * project at all; this one is needed when the answer depends on which
     * permissions that membership carries.
     */
    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Optional<ProjectMember> findByIdAndProjectId(UUID id, UUID projectId);

    @Query("""
            SELECT COUNT(DISTINCT m) FROM ProjectMember m
            JOIN m.roles r
            WHERE m.project.id = :projectId
              AND m.active = true
              AND m.user.active = true
              AND r.memberType = org.enerscope.project.model.enums.ProjectMemberType.ADMIN
            """)
    long countAdminsByProject(@Param("projectId") UUID projectId);

    /**
     * Members of a project with their user and roles already fetched, so mapping
     * to a DTO never triggers a lazy load per row. A constructor projection is
     * not an option here the way it is for {@code ProjectRepository.findSummaries}:
     * the permissions are an {@code @ElementCollection}, and JPQL cannot build a
     * collection into a record.
     */
    @Query("""
            SELECT DISTINCT m FROM ProjectMember m
            JOIN FETCH m.user
            LEFT JOIN FETCH m.roles
            WHERE m.project.id = :projectId
            ORDER BY m.createdAt
            """)
    List<ProjectMember> findByProjectIdWithUser(@Param("projectId") UUID projectId);

    @Query("""
            SELECT m FROM ProjectMember m
            WHERE m.user.id = :userId AND m.project.organization.id = :organizationId
            """)
    List<ProjectMember> findByUserInOrganization(@Param("userId") UUID userId,
                                                 @Param("organizationId") UUID organizationId);
}
