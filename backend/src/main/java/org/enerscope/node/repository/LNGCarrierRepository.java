package org.enerscope.node.repository;

import org.enerscope.node.model.export.LNGCarrier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface LNGCarrierRepository extends JpaRepository<LNGCarrier, UUID> {
    Optional<LNGCarrier> findByIdentityId(UUID id);
}