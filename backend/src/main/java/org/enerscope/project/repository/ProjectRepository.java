package org.enerscope.project.repository;

import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    /**
     * Every project, newest activity first. Projects a platform ADMIN can see.
     *
     * <p>Returns the projection rather than entities: {@code Project.organization}
     * and {@code Project.members} are both lazy, so mapping entities outside a
     * transaction would fail and counting members per row would be an N+1. A
     * null {@code organizationId} disables the organization filter.</p>
     */
    @Query("""
            SELECT new org.enerscope.project.dto.ProjectSummaryDTO(
                    p.id, p.name, p.description, o.id, o.name,
                    (SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.project = p AND pm.active = true),
                    p.lastModified)
            FROM Project p
            JOIN p.organization o
            WHERE p.active = true
              AND (:organizationId IS NULL OR o.id = :organizationId)
            ORDER BY p.lastModified DESC
            """)
    List<ProjectSummaryDTO> findSummaries(@Param("organizationId") UUID organizationId);

    /** As {@link #findSummaries(UUID)}, restricted to projects the user is a member of. */
    @Query("""
            SELECT new org.enerscope.project.dto.ProjectSummaryDTO(
                    p.id, p.name, p.description, o.id, o.name,
                    (SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.project = p AND pm.active = true),
                    p.lastModified)
            FROM Project p
            JOIN p.organization o
            JOIN p.members m
            WHERE p.active = true
              AND m.user.id = :userId
              AND (:organizationId IS NULL OR o.id = :organizationId)
            ORDER BY p.lastModified DESC
            """)
    List<ProjectSummaryDTO> findSummariesForMember(@Param("userId") UUID userId,
                                                   @Param("organizationId") UUID organizationId);

    /**
     * The id of the project a version hangs off, empty when it hangs off none.
     *
     * <p>{@code Project.versions} is a unidirectional {@code @OneToMany}: the
     * foreign key lives on the {@code version} table, but {@code Version} has no
     * field pointing back, so a version cannot reach its project on its own.
     * Returning the id rather than the entity keeps the lazy
     * {@code organization} and {@code members} out of it — callers only need the
     * id to run an authorization check.</p>
     */
    @Query("""
            SELECT p.id FROM Project p JOIN p.versions v
            WHERE v.id = :versionId AND v.active = true AND p.active = true
            """)
    Optional<UUID> findIdByVersionId(@Param("versionId") UUID versionId);

    boolean existsByIdAndActiveTrue(UUID id);
}
