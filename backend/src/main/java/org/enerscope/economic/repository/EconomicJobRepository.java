package org.enerscope.economic.repository;
import java.time.Instant;
import java.util.*;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
public interface EconomicJobRepository extends JpaRepository<EconomicJob,UUID> {
    List<EconomicJob> findByVersionIdOrderByCreatedAtDesc(UUID versionId);
    Optional<EconomicJob> findByIdAndVersionId(UUID id,UUID versionId);
    Optional<EconomicJob> findByEvaluationId(UUID evaluationId);
    Optional<EconomicJob> findFirstByStatusOrderByCreatedAtAsc(EconomicJobStatus status);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update EconomicJob j set j.status=:running, j.startedAt=:now where j.id=:id and j.status=:queued")
    int claim(@Param("id") UUID id,@Param("queued") EconomicJobStatus queued,
        @Param("running") EconomicJobStatus running,@Param("now") Instant now);
    @Modifying(clearAutomatically=true,flushAutomatically=true)
    @Query("update EconomicJob j set j.status=:interrupted,j.finishedAt=:now,j.errorMessage='Server restarted before completion' where j.status in :pending")
    int interruptPending(@Param("pending") Collection<EconomicJobStatus> pending,
        @Param("interrupted") EconomicJobStatus interrupted,@Param("now") Instant now);
}
