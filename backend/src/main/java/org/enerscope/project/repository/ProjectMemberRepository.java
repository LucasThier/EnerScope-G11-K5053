package org.enerscope.project.repository;

import org.enerscope.project.model.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {
    boolean existsByProjectIdAndUserId(UUID projectId, UUID userId);

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
}
