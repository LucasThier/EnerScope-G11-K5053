package org.enerscope.economic.model;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.enerscope.common.BaseEntity;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.version.model.Version;

@Entity
@Table(name="economic_job")
@Getter
@NoArgsConstructor
public class EconomicJob extends BaseEntity {
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="version_id",nullable=false)
    private Version version;

    @Column(name="input_json",nullable=false,updatable=false,columnDefinition="text")
    private String inputJson;

    @Enumerated(EnumType.STRING) @Column(nullable=false,length=24)
    private EconomicJobStatus status;

    @Column(nullable=false)
    private int rounds;

    @Column(name="completed_rounds",nullable=false)
    private int completedRounds;

    @Column(nullable=false,length=20,updatable=false)
    private String seed;

    @Column(name="started_at")
    private Instant startedAt;

    @Column(name="finished_at")
    private Instant finishedAt;

    @Column(name="error_message",columnDefinition="text")
    private String errorMessage;

    @Column(name="evaluation_id")
    private UUID evaluationId;

    public EconomicJob(Version version,String inputJson,int rounds,String seed) {
        this.version = version;
        this.inputJson = inputJson;
        this.rounds = rounds;
        this.seed = seed;
        status = EconomicJobStatus.QUEUED;
    }

    public void roundCompleted(int round) {
        if(this.status != EconomicJobStatus.RUNNING || round != completedRounds+1 || round > rounds)
            throw new IllegalStateException("Invalid round transition");
        completedRounds = round;
    }

    public void complete(UUID evaluationId) {
        if(this.status != EconomicJobStatus.RUNNING || completedRounds != this.rounds)
            throw new IllegalStateException("Incomplete evaluation");
        this.evaluationId = evaluationId;
        this.status = EconomicJobStatus.COMPLETED;
        this.finishedAt = Instant.now();
    }

    public void fail(String message, boolean interrupted) {
        if(status != EconomicJobStatus.RUNNING)
            return;
        status = interrupted ? EconomicJobStatus.INTERRUPTED : EconomicJobStatus.FAILED;
        errorMessage = message;
        finishedAt = Instant.now();
    }
}
