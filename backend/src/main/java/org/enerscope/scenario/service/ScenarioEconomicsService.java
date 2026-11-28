package org.enerscope.scenario.service;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.dto.EconomicJobDTO;
import org.enerscope.economic.dto.EconomicJobRequest;
import org.enerscope.economic.dto.EconomicRoundSummary;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.economic.service.EconomicJobService;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The economic configuration, the simulations and the results of one scenario addressed by its own id.
 * It only authorizes (see {@link ScenarioAccess}) and delegates to the services that also serve the old
 * {@code /versions/{id}/...} endpoints, which act on the version's default scenario.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioEconomicsService {
    private final ScenarioAccess access;
    private final ScenarioConfigurationService configuration;
    private final EconomicService economics;
    private final EconomicJobService jobs;

    @Transactional(readOnly = true)
    public ScenarioConfigurationDTO configuration(UUID projectId, UUID scenarioId) {
        var resolved = access.authorizeView(projectId, scenarioId);
        return configuration.viewOf(resolved.version(), resolved.scenario());
    }

    public ScenarioConfigurationDTO saveConfiguration(UUID projectId, UUID scenarioId, ScenarioConfigurationDTO.SaveRequest request) {
        var resolved = access.authorizeEdit(projectId, scenarioId);
        return configuration.saveIn(resolved.version(), resolved.scenario(), request);
    }

    public ScenarioConfigurationDTO discardDraft(UUID projectId, UUID scenarioId, String token) {
        var resolved = access.authorizeEdit(projectId, scenarioId);
        return configuration.discardIn(resolved.version(), resolved.scenario(), token);
    }

    public EconomicJobDTO simulate(UUID projectId, UUID scenarioId, EconomicJobRequest request) {
        var resolved = access.authorizeSimulate(projectId, scenarioId);
        return jobs.submitIn(resolved.version(), resolved.scenario(), request);
    }

    public EconomicJobDTO retry(UUID projectId, UUID scenarioId, UUID jobId) {
        var resolved = access.authorizeSimulate(projectId, scenarioId);
        return jobs.retryIn(resolved.version(), resolved.scenario(), jobId);
    }

    @Transactional(readOnly = true)
    public List<EconomicJobDTO> simulations(UUID projectId, UUID scenarioId) {
        return jobs.jobsOf(access.authorizeView(projectId, scenarioId).scenario());
    }

    @Transactional(readOnly = true)
    public EconomicJobDTO simulation(UUID projectId, UUID scenarioId, UUID jobId) {
        return jobs.jobOf(access.authorizeView(projectId, scenarioId).scenario(), jobId);
    }

    @Transactional(readOnly = true)
    public List<EvaluationDTO> evaluations(UUID projectId, UUID scenarioId) {
        return economics.evaluationsOf(access.authorizeView(projectId, scenarioId).scenario());
    }

    @Transactional(readOnly = true)
    public EvaluationDTO evaluation(UUID projectId, UUID scenarioId, UUID evaluationId) {
        return economics.evaluationOf(access.authorizeView(projectId, scenarioId).scenario(), evaluationId);
    }

    @Transactional(readOnly = true)
    public List<EconomicRoundSummary> rounds(UUID projectId, UUID scenarioId, UUID evaluationId) {
        return jobs.roundSummariesOf(access.authorizeView(projectId, scenarioId).scenario(), evaluationId);
    }

    @Transactional(readOnly = true)
    public EconomicRoundResult round(UUID projectId, UUID scenarioId, UUID evaluationId, int round) {
        return jobs.roundOf(access.authorizeView(projectId, scenarioId).scenario(), evaluationId, round);
    }
}
