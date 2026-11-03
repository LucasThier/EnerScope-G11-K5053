package org.enerscope.comparison.repository;

import java.util.*;
import org.enerscope.comparison.model.ScenarioComparison;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ComparisonRepository extends JpaRepository<ScenarioComparison, UUID> {
    List<ScenarioComparison> findByProjectIdOrderByCreatedAtDesc(UUID projectId);
    Optional<ScenarioComparison> findByIdAndProjectId(UUID id, UUID projectId);
}
