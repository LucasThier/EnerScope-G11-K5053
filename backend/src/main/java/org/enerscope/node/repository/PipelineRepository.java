package org.enerscope.node.repository;

import org.enerscope.node.model.transportation.Pipeline;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface PipelineRepository extends JpaRepository<Pipeline, UUID> {
    Optional<Pipeline> findByIdentityId(UUID id);
}