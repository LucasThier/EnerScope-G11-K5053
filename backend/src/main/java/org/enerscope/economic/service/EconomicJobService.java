package org.enerscope.economic.service;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.dto.*;
import org.enerscope.economic.model.*;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.model.results.*;
import org.enerscope.economic.repository.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static org.enerscope.economic.model.enums.EconomicJobStatus.*;

@Service @RequiredArgsConstructor @Transactional
public class EconomicJobService {
    private final EconomicService economics;
    private final EconomicValidator validator;
    private final EconomicInputCodec codec;
    private final EconomicJobRepository jobs;
    private final EconomicRoundRepository rounds;
    private final EconomicEvaluationRepository evaluations;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    public EconomicJobDTO submit(UUID projectId,UUID versionId,EconomicJobRequest request) {
        var version=economics.authorize(projectId,versionId,ProjectMemberPermission.EDIT_PROJECT);
        economics.assertNoDraft(version);
        var configuration=economics.configuration(versionId);
        validator.validate(configuration,economics.nodeIds(version));
        economics.validateExportNodes(configuration,version);
        if(request==null) request=new EconomicJobRequest(null,null);
        var input=codec.freeze(version,configuration,request.roundCount(),request.seedValue());
        var stored=jobs.saveAndFlush(new EconomicJob(version,json(input),input.simulation().rounds(),input.simulation().seed()));
        logger.info("Economic job {} queued for version {}",stored.getId(),versionId);
        return dto(stored);
    }

    @Transactional(readOnly=true)
    public List<EconomicJobDTO> list(UUID projectId,UUID versionId) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        return jobs.findByVersionIdOrderByCreatedAtDesc(versionId).stream().map(this::dto).toList();
    }

    @Transactional(readOnly=true)
    public EconomicJobDTO get(UUID projectId,UUID versionId,UUID id) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        return dto(find(versionId,id));
    }

    public EconomicJobDTO retry(UUID projectId,UUID versionId,UUID id) {
        var version=economics.authorize(projectId,versionId,ProjectMemberPermission.EDIT_PROJECT);
        var old=find(versionId,id);
        if(old.getStatus()!=FAILED && old.getStatus()!=INTERRUPTED)
            throw new IllegalArgumentException("Only failed or interrupted jobs can be repeated");
        codec.thaw(read(old.getInputJson(),FrozenEconomicInput.class));
        var copy=jobs.saveAndFlush(new EconomicJob(version,old.getInputJson(),old.getRounds(),old.getSeed()));
        logger.info("Economic job {} repeats job {}",copy.getId(),id);
        return dto(copy);
    }

    @Transactional(readOnly=true)
    public List<EconomicRoundSummary> roundSummaries(UUID projectId,UUID versionId,UUID evaluationId) {
        var job=completedJob(projectId,versionId,evaluationId);
        return rounds.summaries(job.getId()).stream().map(s->read(s,EconomicRoundSummary.class)).toList();
    }

    @Transactional(readOnly=true)
    public EconomicRoundResult round(UUID projectId,UUID versionId,UUID evaluationId,int number) {
        var job=completedJob(projectId,versionId,evaluationId);
        return read(rounds.findByJobIdAndRoundNumber(job.getId(),number)
                .orElseThrow(()->new EntityNotFoundException("Economic round not found")).getSnapshotJson(),EconomicRoundResult.class);
    }

    private EconomicJob completedJob(UUID projectId,UUID versionId,UUID evaluationId) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        if(evaluations.findByIdAndVersionId(evaluationId,versionId).isEmpty())
            throw new EntityNotFoundException("Economic evaluation not found");
        return jobs.findByEvaluationId(evaluationId).filter(j->j.getStatus()==COMPLETED)
                .orElseThrow(()->new EntityNotFoundException("Evaluation has no probabilistic rounds"));
    }

    public record ClaimedJob(UUID id,FrozenEconomicInput input) {}
    public ClaimedJob claimNext() {
        var candidate=jobs.findFirstByStatusOrderByCreatedAtAsc(QUEUED);
        if(candidate.isEmpty()) return null;
        UUID id=candidate.get().getId();
        if(jobs.claim(id,QUEUED,RUNNING,Instant.now())!=1) return null;
        logger.info("Economic job {} started",id);
        return new ClaimedJob(id,read(jobs.findById(id).orElseThrow().getInputJson(),FrozenEconomicInput.class));
    }

    public void saveRound(UUID id,EconomicRoundResult result) {
        var job=jobs.findById(id).orElseThrow();
        job.roundCompleted(result.round());
        var summary = new EconomicRoundSummary(result.round(),result.seed(),result.result().npv(),result.result().indicators());
        rounds.save(new EconomicRound(job,result.round(),json(result),json(summary)));
    }

    public void complete(UUID id,ProbabilisticSummary summary) {
        var job=jobs.findById(id).orElseThrow();
        if(job.getStatus()!=RUNNING || job.getCompletedRounds()!=job.getRounds() || summary.rounds()!=job.getRounds())
            throw new IllegalStateException("Incomplete economic job");
        var input=read(job.getInputJson(),FrozenEconomicInput.class);
        var snapshot=new EvaluationDTO.Snapshot(3,input.configuration(),codec.displayNodes(input),input.connections(),
                null,null,input.simulation(),summary);
        var evaluation=evaluations.saveAndFlush(new EconomicEvaluation(job.getVersion(),json(snapshot)));
        job.complete(evaluation.getId());
        logger.info("Economic job {} completed as evaluation {}",id,evaluation.getId());
    }

    public void fail(UUID id,String message,boolean interrupted) {
        jobs.findById(id).ifPresent(job->job.fail(message,interrupted));
    }

    public void interruptPending() {
        int count=jobs.interruptPending(List.of(QUEUED,RUNNING),INTERRUPTED,Instant.now());
        if(count>0) logger.warn("Marked {} unfinished economic jobs as interrupted",count);
    }

    private EconomicJob find(UUID versionId,UUID id) {
        return jobs.findByIdAndVersionId(id,versionId).orElseThrow(()->new EntityNotFoundException("Economic job not found"));
    }

    private EconomicJobDTO dto(EconomicJob job) {
        return new EconomicJobDTO(job.getId(),job.getStatus(),job.getRounds(),job.getCompletedRounds(),job.getSeed(),
                job.getCreatedAt(),job.getStartedAt(),job.getFinishedAt(),job.getErrorMessage(),job.getEvaluationId());
    }

    private String json(Object value) {
        try {return mapper.writeValueAsString(value);}
        catch(JsonProcessingException error){throw new IllegalStateException("Cannot serialize economic job",error);}
    }

    private <T> T read(String json,Class<T> type) {
        try{return mapper.readValue(json,type);}
        catch(JsonProcessingException error){throw new IllegalStateException("Cannot read economic job",error);}
    }
}
