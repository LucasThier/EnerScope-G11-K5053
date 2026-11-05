package org.enerscope.economic.dto;
import java.time.Instant;
import java.util.UUID;
import org.enerscope.economic.model.enums.EconomicJobStatus;
public record EconomicJobDTO(UUID id,
                             EconomicJobStatus status,
                             int rounds,
                             int completedRounds,
                             String seed,
                             Instant createdAt,
                             Instant startedAt,
                             Instant finishedAt,
                             String errorMessage,
                             UUID evaluationId,
                             EvaluationDTO.RevisionTag revision) {
    public EconomicJobDTO(UUID id,EconomicJobStatus status,int rounds,int completedRounds,String seed,
                          Instant createdAt,Instant startedAt,Instant finishedAt,String errorMessage,UUID evaluationId) {
        this(id,status,rounds,completedRounds,seed,createdAt,startedAt,finishedAt,errorMessage,evaluationId,null);
    }
}
