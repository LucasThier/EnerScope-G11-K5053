package org.enerscope.project.repository;

import org.enerscope.project.dto.ProjectSummaryDTO;
import org.enerscope.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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
                    (SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.project = p),
                    p.lastModified)
            FROM Project p
            JOIN p.organization o
            WHERE (:organizationId IS NULL OR o.id = :organizationId)
            ORDER BY p.lastModified DESC
            """)
    List<ProjectSummaryDTO> findSummaries(@Param("organizationId") UUID organizationId);

    /** As {@link #findSummaries(UUID)}, restricted to projects the user is a member of. */
    @Query("""
            SELECT new org.enerscope.project.dto.ProjectSummaryDTO(
                    p.id, p.name, p.description, o.id, o.name,
                    (SELECT COUNT(pm) FROM ProjectMember pm WHERE pm.project = p),
                    p.lastModified)
            FROM Project p
            JOIN p.organization o
            JOIN p.members m
            WHERE m.user.id = :userId
              AND (:organizationId IS NULL OR o.id = :organizationId)
            ORDER BY p.lastModified DESC
            """)
    List<ProjectSummaryDTO> findSummariesForMember(@Param("userId") UUID userId,
                                                   @Param("organizationId") UUID organizationId);
}
