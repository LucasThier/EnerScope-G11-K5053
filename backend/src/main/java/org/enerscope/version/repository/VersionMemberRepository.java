package org.enerscope.version.repository;

import org.enerscope.version.model.VersionMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionMemberRepository extends JpaRepository<VersionMember, UUID> {

    /**
     * The caller's membership row, roles included, for permission checks.
     * Mirrors {@code ProjectMemberRepository.findByProjectIdAndUserId}.
     */
    Optional<VersionMember> findByVersionIdAndUserId(UUID versionId, UUID userId);

    @Query("""
            SELECT m FROM VersionMember m
            WHERE m.version.id = :versionId
              AND m.user.id = :userId
              AND EXISTS (
                  SELECT 1 FROM Project p JOIN p.versions v JOIN p.members pm
                  WHERE v.id = :versionId AND v.active = true AND p.active = true
                    AND pm.user.id = :userId AND pm.active = true)
            """)
    Optional<VersionMember> findEffectiveByVersionIdAndUserId(@Param("versionId") UUID versionId,
                                                              @Param("userId") UUID userId);

    @Query("""
            SELECT m FROM VersionMember m
            WHERE m.user.id = :userId
              AND m.version.id IN (
                  SELECT v.id FROM Project p JOIN p.versions v
                  WHERE p.id IN :projectIds)
            """)
    List<VersionMember> findByUserIdAndProjectIdIn(@Param("userId") UUID userId,
                                                   @Param("projectIds") Collection<UUID> projectIds);

    /**
     * Removes every membership (and, by cascade, its roles and permissions) of the given versions. Needed before a physical
     * delete of the versions: {@code version_member.version_id} has no ON DELETE CASCADE.
     */
    void deleteByVersionIdIn(Collection<UUID> versionIds);

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
              AND EXISTS (
                  SELECT 1 FROM Project p JOIN p.versions v JOIN p.members pm
                  WHERE v = m.version AND pm.user = m.user)
            ORDER BY m.createdAt
            """)
    List<VersionMember> findByVersionIdWithUser(@Param("versionId") UUID versionId);

    /**
     * Same members as {@link #findByVersionIdWithUser} (including the
     * project-membership filter), for every active version of a project in one
     * query, user and roles fetched.
     */
    @Query("""
            SELECT DISTINCT m FROM VersionMember m
            JOIN FETCH m.user
            LEFT JOIN FETCH m.roles
            WHERE m.version.id IN (
                  SELECT v.id FROM Project p JOIN p.versions v
                  WHERE p.id = :projectId AND v.active = true)
              AND EXISTS (
                  SELECT 1 FROM Project p JOIN p.versions v JOIN p.members pm
                  WHERE v = m.version AND pm.user = m.user)
            ORDER BY m.createdAt
            """)
    List<VersionMember> findByProjectIdWithUser(@Param("projectId") UUID projectId);

    @Query("""
            SELECT DISTINCT m FROM VersionMember m
            LEFT JOIN FETCH m.roles
            WHERE m.user.id = :userId AND m.version.id IN :versionIds
            """)
    List<VersionMember> findByUserIdAndVersionIdIn(@Param("userId") UUID userId,
                                                   @Param("versionIds") Collection<UUID> versionIds);
}
