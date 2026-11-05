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
    @org.springframework.beans.factory.annotation.Autowired private org.enerscope.scenario.service.ScenarioRevisionService revisions;

    public EconomicJobDTO submit(UUID projectId,UUID versionId,EconomicJobRequest request) {
        var version=economics.authorize(projectId,versionId,ProjectMemberPermission.EDIT_PROJECT);
        if(revisions!=null)revisions.assertCanSimulate(versionId);
        economics.assertNoDraft(version);
        var configuration=economics.configuration(versionId);
        validator.validate(configuration,economics.nodeIds(version));
        economics.validateExportNodes(configuration,version);
        if(request==null) request=new EconomicJobRequest(null,null);
        var input=codec.freeze(version,configuration,request.roundCount(),request.seedValue());
        var candidate = revisions == null ? new Simulation(version,json(input),input.simulation().rounds(),input.simulation().seed())
                : Simulation.forRevision(version,revisions.current(version).getId(),json(input.simulation()),input.simulation().rounds(),input.simulation().seed());
        var stored=jobs.saveAndFlush(candidate);
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
        if(revisions!=null)revisions.assertCanSimulate(versionId);
        var old=find(versionId,id);
        if(old.getStatus()!=FAILED && old.getStatus()!=INTERRUPTED)
            throw new IllegalArgumentException("Only failed or interrupted jobs can be repeated");
        codec.thaw(input(old));
        var copy=jobs.saveAndFlush(old.getScenarioRevisionId()==null
                ? new Simulation(version,old.getInputJson(),old.getRounds(),old.getSeed())
                : Simulation.forRevision(version,old.getScenarioRevisionId(),old.getInputJson(),old.getRounds(),old.getSeed()));
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

    private Simulation completedJob(UUID projectId,UUID versionId,UUID evaluationId) {
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
        return new ClaimedJob(id,null);
    }

    @Transactional(readOnly=true)
    public FrozenEconomicInput loadClaimedInput(UUID id) {
        var job=jobs.findById(id).orElseThrow();
        if(job.getStatus()!=RUNNING) throw new IllegalStateException("Simulation is not running");
        return input(job);
    }

    public void saveRound(UUID id,EconomicRoundResult result) {
        var job=jobs.findById(id).orElseThrow();
        job.roundCompleted(result.round());
        var summary = new EconomicRoundSummary(result.round(),result.seed(),result.result().npv(),result.result().indicators());
        rounds.save(new SimulationRound(job,result.round(),json(result),json(summary)));
    }

    public void complete(UUID id,ProbabilisticSummary summary) {
        var job=jobs.findById(id).orElseThrow();
        if(job.getStatus()!=RUNNING || job.getCompletedRounds()!=job.getRounds() || summary.rounds()!=job.getRounds())
            throw new IllegalStateException("Incomplete economic job");
        var input=input(job);
        var snapshot=new EvaluationDTO.Snapshot(job.getScenarioRevisionId()==null?3:4,job.getScenarioRevisionId()==null?input.configuration():null,job.getScenarioRevisionId()==null?codec.displayNodes(input):null,job.getScenarioRevisionId()==null?input.connections():null,
                null,null,input.simulation(),summary);
        var evaluation=evaluations.saveAndFlush(new EconomicEvaluation(job.getVersion(),json(snapshot),job.getScenarioRevisionId()));
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

    private FrozenEconomicInput input(Simulation job) {
        if(job.getEconomicCalculationVersion()!=1) throw new IllegalArgumentException("Unsupported economic calculation version");
        if(job.getScenarioRevisionId()==null)return read(job.getInputJson(),FrozenEconomicInput.class);
        var tree=read(job.getInputJson(),com.fasterxml.jackson.databind.JsonNode.class);
        var metadata=tree.has("simulation")?mapper.convertValue(tree.get("simulation"),SimulationMetadata.class)
                :mapper.convertValue(tree,SimulationMetadata.class);
        return revisions.executionInput(job.getScenarioRevisionId(),metadata);
    }

    private Simulation find(UUID versionId,UUID id) {
        return jobs.findByIdAndVersionId(id,versionId).orElseThrow(()->new EntityNotFoundException("Economic job not found"));
    }

    private EconomicJobDTO dto(Simulation job) {
        return new EconomicJobDTO(job.getId(),job.getStatus(),job.getRounds(),job.getCompletedRounds(),job.getSeed(),
                job.getCreatedAt(),job.getStartedAt(),job.getFinishedAt(),job.getErrorMessage(),job.getEvaluationId(),
                revisions==null?null:revisions.tag(job.getScenarioRevisionId()));
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
