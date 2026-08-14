package org.enerscope.node.repository;

import org.enerscope.node.model.BaseNode;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BaseNodeRepository extends JpaRepository<BaseNode, UUID> {
    Optional<BaseNode> findByIdentityId(UUID id);
}