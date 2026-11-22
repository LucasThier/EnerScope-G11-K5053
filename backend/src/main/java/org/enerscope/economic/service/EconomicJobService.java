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
import org.enerscope.scenario.model.Scenario;
import org.enerscope.version.model.Version;
import org.enerscope.scenario.service.ScenarioDefaults;
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
    private final ScenarioDefaults scenarioDefaults;

    public EconomicJobDTO submit(UUID projectId,UUID versionId,EconomicJobRequest request) {
        var version=economics.authorizeSimulation(projectId,versionId);
        economics.lock(version);
        return submitIn(version,scenarioDefaults.forVersion(version),request);
    }

    /** Queues a simulation of {@code scenario}; the caller has authorized the simulation. */
    public EconomicJobDTO submitIn(Version version,Scenario scenario,EconomicJobRequest request) {
        economics.assertNoDraft(version,scenario);
        var configuration=economics.configuration(scenario);
        validator.validate(configuration,economics.nodeIds(version));
        economics.validateExportNodes(configuration,version);
        if(request==null) request=new EconomicJobRequest(null,null);
        var input=codec.freeze(version,configuration,request.roundCount(),request.seedValue());
        var queued=new EconomicJob(version,json(input),input.simulation().rounds(),input.simulation().seed());
        queued.assignScenario(scenario);
        var stored=jobs.saveAndFlush(queued);
        logger.info("Economic job {} queued for scenario {} of version {}",stored.getId(),scenario.getId(),version.getId());
        return dto(stored);
    }

    @Transactional(readOnly=true)
    public List<EconomicJobDTO> list(UUID projectId,UUID versionId) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        return scenarioDefaults.existing(versionId).stream().flatMap(s->jobsOf(s).stream()).toList();
    }

    /** The jobs of a scenario, newest first. */
    @Transactional(readOnly=true)
    public List<EconomicJobDTO> jobsOf(Scenario scenario) {
        return jobs.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().map(this::dto).toList();
    }

    /** One job of a scenario; another scenario's job is a 404. */
    @Transactional(readOnly=true)
    public EconomicJobDTO jobOf(Scenario scenario,UUID id) {
        return dto(findIn(scenario,id));
    }

    @Transactional(readOnly=true)
    public EconomicJobDTO get(UUID projectId,UUID versionId,UUID id) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        return dto(find(versionId,id));
    }

    public EconomicJobDTO retry(UUID projectId,UUID versionId,UUID id) {
        var version=economics.authorizeSimulation(projectId,versionId);
        return repeat(version,find(versionId,id));
    }

    /** Repeats a failed or interrupted job of {@code scenario}; the caller has authorized the simulation. */
    public EconomicJobDTO retryIn(Version version,Scenario scenario,UUID id) {
        return repeat(version,findIn(scenario,id));
    }

    private EconomicJobDTO repeat(Version version,EconomicJob old) {
        var id=old.getId();
        if(old.getStatus()!=FAILED && old.getStatus()!=INTERRUPTED)
            throw new IllegalArgumentException("Only failed or interrupted jobs can be repeated");
        codec.thaw(read(old.getInputJson(),FrozenEconomicInput.class));
        var repeated=new EconomicJob(version,old.getInputJson(),old.getRounds(),old.getSeed());
        repeated.assignScenario(old.getScenario()!=null ? old.getScenario() : scenarioDefaults.forVersion(version));
        var copy=jobs.saveAndFlush(repeated);
        logger.info("Economic job {} repeats job {}",copy.getId(),id);
        return dto(copy);
    }

    @Transactional(readOnly=true)
    public List<EconomicRoundSummary> roundSummaries(UUID projectId,UUID versionId,UUID evaluationId) {
        var job=completedJob(projectId,versionId,evaluationId);
        return rounds.summaries(job.getId()).stream().map(s->read(s,EconomicRoundSummary.class)).toList();
    }

    @Transactional(readOnly=true)
    public List<EconomicRoundSummary> roundSummariesOf(Scenario scenario,UUID evaluationId) {
        var job=completedJobOf(scenario,evaluationId);
        return rounds.summaries(job.getId()).stream().map(s->read(s,EconomicRoundSummary.class)).toList();
    }

    @Transactional(readOnly=true)
    public EconomicRoundResult roundOf(Scenario scenario,UUID evaluationId,int number) {
        var job=completedJobOf(scenario,evaluationId);
        return read(rounds.findByJobIdAndRoundNumber(job.getId(),number)
                .orElseThrow(()->new EntityNotFoundException("Economic round not found")).getSnapshotJson(),EconomicRoundResult.class);
    }

    private EconomicJob completedJobOf(Scenario scenario,UUID evaluationId) {
        if(evaluations.findByIdAndScenarioId(evaluationId,scenario.getId()).isEmpty())
            throw new EntityNotFoundException("Economic evaluation not found");
        return jobs.findByEvaluationId(evaluationId).filter(j->j.getStatus()==COMPLETED)
                .orElseThrow(()->new EntityNotFoundException("Evaluation has no probabilistic rounds"));
    }

    @Transactional(readOnly=true)
    public EconomicRoundResult round(UUID projectId,UUID versionId,UUID evaluationId,int number) {
        var job=completedJob(projectId,versionId,evaluationId);
        return read(rounds.findByJobIdAndRoundNumber(job.getId(),number)
                .orElseThrow(()->new EntityNotFoundException("Economic round not found")).getSnapshotJson(),EconomicRoundResult.class);
    }

    private EconomicJob completedJob(UUID projectId,UUID versionId,UUID evaluationId) {
        economics.authorize(projectId,versionId,ProjectMemberPermission.VIEW_PROJECT);
        if(scenarioDefaults.existing(versionId).flatMap(s->evaluations.findByIdAndScenarioId(evaluationId,s.getId())).isEmpty())
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
        var finished=new EconomicEvaluation(job.getVersion(),json(snapshot));
        finished.assignScenario(job.getScenario()!=null ? job.getScenario() : scenarioDefaults.forVersion(job.getVersion()));
        var evaluation=evaluations.saveAndFlush(finished);
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

    private EconomicJob findIn(Scenario scenario,UUID id) {
        return jobs.findByIdAndScenarioId(id,scenario.getId()).orElseThrow(()->new EntityNotFoundException("Economic job not found"));
    }

    private EconomicJob find(UUID versionId,UUID id) {
        return scenarioDefaults.existing(versionId).flatMap(s->jobs.findByIdAndScenarioId(id,s.getId()))
                .orElseThrow(()->new EntityNotFoundException("Economic job not found"));
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
