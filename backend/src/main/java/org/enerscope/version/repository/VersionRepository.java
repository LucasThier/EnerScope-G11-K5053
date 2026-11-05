package org.enerscope.version.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.enerscope.version.model.Version;

import java.util.List;
import java.util.UUID;

public interface VersionRepository extends JpaRepository<Version, UUID> {

    public List<Version> findByParentVersionId(UUID parentVersion);

    @org.springframework.data.jpa.repository.Query("select count(v) from Version v join v.nodeSnapshot n where n.id=:nodeId and v.id<>:versionId")
    long otherNodeOwners(@org.springframework.data.repository.query.Param("nodeId") UUID nodeId,
                         @org.springframework.data.repository.query.Param("versionId") UUID versionId);

}
