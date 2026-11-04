package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.SimulationRound;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EconomicRoundRepository extends JpaRepository<SimulationRound,UUID> {
    Optional<SimulationRound> findByJobIdAndRoundNumber(UUID jobId,int roundNumber);
    @Query("select r.summaryJson from SimulationRound r where r.job.id=:jobId order by r.roundNumber")
    List<String> summaries(@Param("jobId") UUID jobId);
}
