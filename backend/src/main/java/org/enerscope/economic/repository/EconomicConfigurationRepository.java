package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EconomicConfigurationRepository extends JpaRepository<EconomicConfigurationEntity, UUID> {
    /** The configuration of a scenario (V27: one per scenario). */
    Optional<EconomicConfigurationEntity> findByScenarioId(UUID scenarioId);

    /** The stored configurations of many scenarios in one query. */
    java.util.List<EconomicConfigurationEntity> findByScenarioIdIn(java.util.Collection<UUID> scenarioIds);

    /** Which of these scenarios have a stored configuration (one query for a whole list). */
    @org.springframework.data.jpa.repository.Query("select c.scenario.id from EconomicConfigurationEntity c where c.scenario.id in :ids")
    java.util.List<UUID> scenarioIdsWithConfiguration(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    /** Every scenario's configuration in a version (to keep their node references coherent). */
    java.util.List<EconomicConfigurationEntity> findAllByVersionId(UUID versionId);

    /** Only valid while a version has a single scenario; tests and the E7 clean-up use it, services read by scenario. */
    Optional<EconomicConfigurationEntity> findByVersionId(UUID versionId);
}
