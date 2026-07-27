package org.enerscope.node.repository;

import org.enerscope.node.model.extraction.TreatmentPlant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface TreatmentPlantRepository extends JpaRepository<TreatmentPlant, UUID> {
    Optional<TreatmentPlant> findByIdentityId(UUID id);
}