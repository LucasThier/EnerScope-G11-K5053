package org.enerscope.node.repository;

import org.enerscope.node.model.export.SeaportTerminal;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface SeaportTerminalRepository extends JpaRepository<SeaportTerminal, UUID> {
    Optional<SeaportTerminal> findByIdentityId(UUID id);
}