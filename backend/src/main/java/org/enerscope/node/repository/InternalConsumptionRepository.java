package org.enerscope.node.repository;

import org.enerscope.node.model.export.InternalConsumption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InternalConsumptionRepository extends JpaRepository<InternalConsumption, UUID> {
    Optional<InternalConsumption> findByIdentityId(UUID id);
}
