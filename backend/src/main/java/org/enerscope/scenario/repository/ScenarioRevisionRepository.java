package org.enerscope.scenario.repository;
import org.enerscope.scenario.model.ScenarioRevision;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
public interface ScenarioRevisionRepository extends JpaRepository<ScenarioRevision, UUID> {
    @org.springframework.data.jpa.repository.Query("select coalesce(max(r.number),0) from ScenarioRevision r where r.scenarioId=:id")
    int maximumNumber(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<ScenarioRevision> findFirstByScenarioIdAndContentHash(UUID scenarioId,String contentHash);
    List<ScenarioRevision> findByScenarioIdOrderByNumberDesc(UUID scenarioId);
    Optional<ScenarioRevision> findByIdAndScenarioId(UUID id, UUID scenarioId);
}
