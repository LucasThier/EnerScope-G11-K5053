package org.enerscope.scenario.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.enerscope.scenario.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
    Optional<Scenario> findByVersionIdAndDefaultScenarioTrueAndActiveTrue(UUID versionId);

    /** The default scenarios of many versions in one query (project-wide screens). */
    List<Scenario> findByVersionIdInAndDefaultScenarioTrueAndActiveTrue(Collection<UUID> versionIds);

    /** A version's active scenarios, oldest first (the oldest becomes the default when the default is deleted). */
    List<Scenario> findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(UUID versionId);

    /** The active scenarios of many versions in one query, oldest first. */
    List<Scenario> findByVersionIdInAndActiveTrueOrderByCreatedAtAscIdAsc(Collection<UUID> versionIds);

    long countByVersionIdAndActiveTrue(UUID versionId);

    boolean existsByVersionIdAndNameIgnoreCaseAndActiveTrue(UUID versionId, String name);

    boolean existsByVersionIdAndNameIgnoreCaseAndActiveTrueAndIdNot(UUID versionId, String name, UUID id);
}
