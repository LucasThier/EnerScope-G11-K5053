package org.enerscope.version.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.enerscope.version.model.ConflictEntityType;
import org.enerscope.version.model.VersionConflict;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VersionConflictRepository extends JpaRepository<VersionConflict, UUID> {
    List<VersionConflict> findByConflictingVersionId(UUID conflictingVersionId);

    List<VersionConflict> findByMergedVersionId(UUID mergedVersionId);

    @Query("""
            SELECT vc FROM VersionConflict vc
            WHERE vc.mergedVersion.id = :mergedVersionId
              AND vc.sourceVersion.id = :sourceVersionId
              AND vc.conflictingVersion.id = :conflictingVersionId
              AND vc.entityType = :entityType
              AND vc.identityId = :identityId
              AND vc.resolved = false
            ORDER BY vc.createdAt ASC
            """)
    List<VersionConflict> findPendingDuplicates(
            @Param("mergedVersionId") UUID mergedVersionId,
            @Param("sourceVersionId") UUID sourceVersionId,
            @Param("conflictingVersionId") UUID conflictingVersionId,
            @Param("entityType") ConflictEntityType entityType,
            @Param("identityId") UUID identityId,
            Pageable pageable);

    default Optional<VersionConflict> findPendingDuplicate(UUID mergedVersionId, UUID sourceVersionId,
            UUID conflictingVersionId, ConflictEntityType entityType, UUID identityId) {
        List<VersionConflict> matches = findPendingDuplicates(mergedVersionId, sourceVersionId, conflictingVersionId,
                entityType, identityId, PageRequest.of(0, 1));
        return matches.isEmpty() ? Optional.empty() : Optional.of(matches.get(0));
    }
}
