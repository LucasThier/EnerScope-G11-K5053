package org.enerscope.node.repository;

import org.enerscope.node.model.extraction.Well;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface WellRepository extends JpaRepository<Well, UUID> {
    Optional<Well> findByIdentityId(UUID id);
}