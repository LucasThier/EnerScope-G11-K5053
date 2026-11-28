package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EconomicEvaluationRepository extends JpaRepository<EconomicEvaluation, UUID> {
    boolean existsByVersionIdIn(java.util.Collection<UUID> versionIds);
    List<EconomicEvaluation> findByScenarioIdOrderByCreatedAtDesc(UUID scenarioId);
    Optional<EconomicEvaluation> findByIdAndScenarioId(UUID id, UUID scenarioId);
    List<EconomicEvaluation> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    Optional<EconomicEvaluation> findByIdAndVersionId(UUID id, UUID versionId);
}
