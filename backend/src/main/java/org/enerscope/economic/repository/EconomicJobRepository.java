package org.enerscope.economic.repository;
import java.time.Instant;
import java.util.*;
import org.enerscope.economic.model.Simulation;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EconomicJobRepository extends JpaRepository<Simulation,UUID> {
    List<Simulation> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    Optional<Simulation> findByIdAndVersionId(UUID id,UUID versionId);
    Optional<Simulation> findByEvaluationId(UUID evaluationId);
    Optional<Simulation> findFirstByStatusOrderByCreatedAtAsc(EconomicJobStatus status);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update Simulation j set j.status=:running, j.startedAt=:now where j.id=:id and j.status=:queued")
    int claim(@Param("id") UUID id,@Param("queued") EconomicJobStatus queued,
        @Param("running") EconomicJobStatus running,@Param("now") Instant now);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update Simulation j set j.status=:interrupted,j.finishedAt=:now,j.errorMessage='Server restarted before completion' where j.status in :pending")
    int interruptPending(@Param("pending") Collection<EconomicJobStatus> pending,
        @Param("interrupted") EconomicJobStatus interrupted,@Param("now") Instant now);
}
