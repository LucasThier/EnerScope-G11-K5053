package org.enerscope.scenario.repository;
import org.enerscope.scenario.model.ScenarioMerge;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ScenarioMergeRepository extends JpaRepository<ScenarioMerge,UUID> {
 Optional<ScenarioMerge> findByTargetIdAndState(UUID targetId,String state);
 List<ScenarioMerge> findByTargetIdOrderByCreatedAtDesc(UUID targetId);
}
