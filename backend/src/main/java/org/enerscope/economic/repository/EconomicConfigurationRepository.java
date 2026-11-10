package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
public interface EconomicConfigurationRepository extends JpaRepository<EconomicConfigurationEntity, UUID> {
    Optional<EconomicConfigurationEntity> findByVersionId(UUID versionId);
}
