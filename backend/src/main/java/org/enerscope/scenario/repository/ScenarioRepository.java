package org.enerscope.scenario.repository;
import org.enerscope.scenario.model.Scenario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
public interface ScenarioRepository extends JpaRepository<Scenario, UUID> {
    List<Scenario> findByProjectIdOrderByNameAsc(UUID projectId);
}
