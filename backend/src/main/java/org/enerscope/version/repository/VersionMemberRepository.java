package org.enerscope.version.repository;

import org.enerscope.version.model.VersionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionMemberRepository extends JpaRepository<VersionMember, UUID> {

    /**
     * The caller's membership row, roles included, for permission checks.
     * Mirrors {@code ProjectMemberRepository.findByProjectIdAndUserId}.
     */
    Optional<VersionMember> findByVersionIdAndUserId(UUID versionId, UUID userId);

    Optional<VersionMember> findByIdAndVersionId(UUID id, UUID versionId);

    /**
     * Members of a version with their user and roles already fetched, so
     * mapping to a DTO never triggers a lazy load per row.
     */
    @Query("""
            SELECT DISTINCT m FROM VersionMember m
            JOIN FETCH m.user
            LEFT JOIN FETCH m.roles
            WHERE m.version.id = :versionId
            ORDER BY m.createdAt
            """)
    List<VersionMember> findByVersionIdWithUser(@Param("versionId") UUID versionId);
}
