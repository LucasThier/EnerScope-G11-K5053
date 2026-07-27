package org.enerscope.node.repository;

import org.enerscope.node.model.NodeConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface NodeConnectionRepository extends JpaRepository<NodeConnection, UUID> {
    Optional<NodeConnection> findByIdentityId(UUID id);
}