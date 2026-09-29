package org.enerscope.version.repository;

import java.util.List;
import java.util.UUID;

import org.enerscope.version.model.VersionConflict;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VersionConflictRepository extends JpaRepository<VersionConflict, UUID> {
    List<VersionConflict> findByConflictingVersionId(UUID conflictingVersionId);

    List<VersionConflict> findByMergedVersionId(UUID mergedVersionId);
}
