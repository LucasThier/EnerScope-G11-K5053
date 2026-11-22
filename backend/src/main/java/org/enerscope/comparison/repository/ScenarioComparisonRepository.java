package org.enerscope.comparison.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enerscope.comparison.model.ScenarioComparison;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioComparisonRepository extends JpaRepository<ScenarioComparison, UUID> {

    List<ScenarioComparison> findByProjectIdAndActiveTrueOrderByCreatedAtDesc(UUID projectId);

    Optional<ScenarioComparison> findByIdAndProjectIdAndActiveTrue(UUID id, UUID projectId);
}
