package org.enerscope.node.repository;

import org.enerscope.node.model.export.IndustrialConsumption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IndustrialConsumptionRepository extends JpaRepository<IndustrialConsumption, UUID> {
    Optional<IndustrialConsumption> findByIdentityId(UUID id);
}
