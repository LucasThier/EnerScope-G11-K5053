package org.enerscope.node.repository;

import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GroundBasedLiquefactionPlantRepository extends JpaRepository<GroundBasedLiquefactionPlant, UUID> {
    Optional<GroundBasedLiquefactionPlant> findByIdentityId(UUID id);
}