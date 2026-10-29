package org.enerscope.scenario.repository;
import java.util.*;
import org.enerscope.scenario.model.ScenarioDraft;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ScenarioDraftRepository extends JpaRepository<ScenarioDraft,UUID> {
    Optional<ScenarioDraft> findByVersionId(UUID versionId);
    boolean existsByVersionId(UUID versionId);
    void deleteByVersionId(UUID versionId);
}
