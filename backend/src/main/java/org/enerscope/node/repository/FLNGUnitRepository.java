package org.enerscope.node.repository;

import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface FLNGUnitRepository extends JpaRepository<FLNGUnit, UUID> {
    Optional<FLNGUnit> findByIdentityId(UUID id);
}