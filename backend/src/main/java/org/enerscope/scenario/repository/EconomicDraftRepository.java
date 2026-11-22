package org.enerscope.scenario.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.enerscope.scenario.model.EconomicDraftEntity;

public interface EconomicDraftRepository extends JpaRepository<EconomicDraftEntity, UUID> {
    Optional<EconomicDraftEntity> findByScenarioId(UUID scenarioId);
    void deleteByScenarioId(UUID scenarioId);
    /** Which of these scenarios have a pending legacy economic draft (one query for a whole list). */
    @org.springframework.data.jpa.repository.Query("select d.scenario.id from EconomicDraftEntity d where d.scenario.id in :ids")
    java.util.List<UUID> scenarioIdsWithDraft(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    Optional<EconomicDraftEntity> findByVersionId(UUID versionId);
    /** Every scenario of the version can hold its own legacy draft (one per scenario). */
    java.util.List<EconomicDraftEntity> findAllByVersionId(UUID versionId);
    void deleteByVersionId(UUID versionId);
}
