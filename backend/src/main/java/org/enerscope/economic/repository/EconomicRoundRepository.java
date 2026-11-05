package org.enerscope.economic.repository;
import java.util.*;
import org.enerscope.economic.model.EconomicRound;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EconomicRoundRepository extends JpaRepository<EconomicRound,UUID> {
    Optional<EconomicRound> findByJobIdAndRoundNumber(UUID jobId,int roundNumber);
    @Query("select r.summaryJson from EconomicRound r where r.job.id=:jobId order by r.roundNumber")
    List<String> summaries(@Param("jobId") UUID jobId);
}
