package org.enerscope.scenario.repository;
import java.util.*;
import org.enerscope.scenario.model.ScenarioDraft;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioDraftRepository extends JpaRepository<ScenarioDraft,UUID> {
    Optional<ScenarioDraft> findByScenarioId(UUID scenarioId);
    boolean existsByScenarioId(UUID scenarioId);
    void deleteByScenarioId(UUID scenarioId);
    /** Which of these scenarios have a pending scenario draft (one query for a whole list). */
    @org.springframework.data.jpa.repository.Query("select d.scenario.id from ScenarioDraft d where d.scenario.id in :ids")
    java.util.List<UUID> scenarioIdsWithDraft(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);
    Optional<ScenarioDraft> findByVersionId(UUID versionId);
    /** Every scenario of the version can hold its own draft (one per scenario). */
    java.util.List<ScenarioDraft> findAllByVersionId(UUID versionId);
    boolean existsByVersionId(UUID versionId);
    void deleteByVersionId(UUID versionId);
}
