package org.enerscope.economic.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.enerscope.economic.model.EconomicDraftEntity;

public interface EconomicDraftRepository extends JpaRepository<EconomicDraftEntity, UUID> {
    Optional<EconomicDraftEntity> findByVersionId(UUID versionId);
    void deleteByVersionId(UUID versionId);
}
