package org.enerscope.node.repository;

import org.enerscope.node.model.extraction.GatheringNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface GatheringNetworkRepository extends JpaRepository<GatheringNetwork, UUID> {
    Optional<GatheringNetwork> findByIdentityId(UUID id);
}