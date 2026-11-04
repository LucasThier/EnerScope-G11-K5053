package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EconomicEvaluationRepository extends JpaRepository<EconomicEvaluation, UUID> {
    boolean existsByVersionIdAndScenarioRevisionId(UUID versionId,UUID revisionId);
    boolean existsByVersionIdAndScenarioRevisionIdIsNotNull(UUID versionId);
    boolean existsByVersionId(UUID versionId);
    @org.springframework.data.jpa.repository.Query("select max(e.createdAt) from EconomicEvaluation e where e.version.id=:id")
    java.time.Instant latestFinishedAt(@org.springframework.data.repository.query.Param("id") UUID id);
    List<EconomicEvaluation> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    Optional<EconomicEvaluation> findByIdAndVersionId(UUID id, UUID versionId);
}
