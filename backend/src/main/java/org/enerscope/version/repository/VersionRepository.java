package org.enerscope.version.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.enerscope.version.model.Version;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {

    public List<Version> findByParentVersionId(UUID parentVersion);

    /**
     * The id of the project a version belongs to. Versions have no
     * {@code project} field of their own (the link is owned by
     * {@code Project.versions}), so this is the only way to go from a version
     * id to its owning project for authorization checks.
     */
    @Query("SELECT p.id FROM Project p JOIN p.versions v WHERE v.id = :versionId")
    Optional<UUID> findProjectIdByVersionId(@Param("versionId") UUID versionId);

}
