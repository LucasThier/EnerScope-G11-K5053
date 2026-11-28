package org.enerscope.comparison.repository;

import java.util.Collection;
import java.util.UUID;
import org.enerscope.comparison.model.ScenarioComparisonItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioComparisonItemRepository extends JpaRepository<ScenarioComparisonItem, UUID> {
    /** Whether any saved comparison (active or not) holds an item of one of these versions. */
    boolean existsByVersionIdIn(Collection<UUID> versionIds);
}
