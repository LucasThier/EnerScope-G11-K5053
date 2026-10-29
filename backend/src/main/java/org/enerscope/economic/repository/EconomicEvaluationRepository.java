package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicEvaluation;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EconomicEvaluationRepository extends JpaRepository<EconomicEvaluation, UUID> {
    List<EconomicEvaluation> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    Optional<EconomicEvaluation> findByIdAndVersionId(UUID id, UUID versionId);
}
