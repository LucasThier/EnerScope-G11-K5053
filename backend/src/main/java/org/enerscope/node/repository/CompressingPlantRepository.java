package org.enerscope.node.repository;

import org.enerscope.node.model.transportation.CompressingPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface CompressingPlantRepository extends JpaRepository<CompressingPlant, UUID> {
    Optional<CompressingPlant> findByIdentityId(UUID id);
}