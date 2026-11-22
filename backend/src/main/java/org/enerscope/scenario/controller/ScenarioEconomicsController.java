package org.enerscope.scenario.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.dto.EconomicJobDTO;
import org.enerscope.economic.dto.EconomicJobRequest;
import org.enerscope.economic.dto.EconomicRoundSummary;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.SaveRequest;
import org.enerscope.scenario.service.ScenarioEconomicsService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * The economic configuration, simulations and results of one scenario. The old
 * {@code /projects/{p}/versions/{v}/...} routes do the same on the version's default scenario.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/scenarios/{scenarioId}")
@Tag(name = "Scenario economics", description = "Configuration, simulations and results of a scenario")
public class ScenarioEconomicsController {
    private final ScenarioEconomicsService service;

    @GetMapping("/configuration")
    @Operation(summary = "Read a scenario's configuration state",
            description = "Published economic configuration, pending draft and the concurrency tokens. A draft saved before node values "
                    + "moved to the version may carry them: draftOperationsIgnored tells the screen so.")
    public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> configuration(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Scenario configuration", service.configuration(projectId, scenarioId));
    }

    @PutMapping("/configuration")
    @Operation(summary = "Save a scenario's economic configuration",
            description = "Publishes a complete configuration or stores an incomplete one as the scenario's draft. Node values are NOT saved "
                    + "here (they belong to the version); a request that would change them is a 400. 409 when the tokens are stale.")
    public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> saveConfiguration(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                                                   @RequestBody SaveRequest request) {
        return Responses.ok("Scenario saved", service.saveConfiguration(projectId, scenarioId, request));
    }

    @DeleteMapping("/configuration/draft")
    @Operation(summary = "Discard a scenario's pending draft", description = "If-Match carries the token read with the configuration.")
    public ResponseEntity<ApiResponse<ScenarioConfigurationDTO>> discardDraft(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                                              @RequestHeader("If-Match") String token) {
        return Responses.ok("Scenario draft discarded", service.discardDraft(projectId, scenarioId, token));
    }

    @PostMapping("/simulations")
    @Operation(summary = "Simulate a scenario",
            description = "Queues a simulation job. Needs EDIT_PROJECT and the right to simulate the scenario's version; refused (400) while a draft is pending.")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> simulate(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                               @RequestBody(required = false) EconomicJobRequest request) {
        return Responses.accepted("Economic job queued", service.simulate(projectId, scenarioId, request));
    }

    @GetMapping("/simulations")
    @Operation(summary = "List a scenario's simulation jobs")
    public ResponseEntity<ApiResponse<List<EconomicJobDTO>>> simulations(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Economic jobs", service.simulations(projectId, scenarioId));
    }

    @GetMapping("/simulations/{jobId}")
    @Operation(summary = "Read a simulation job of a scenario")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> simulation(@PathVariable UUID projectId, @PathVariable UUID scenarioId, @PathVariable UUID jobId) {
        return Responses.ok("Economic job", service.simulation(projectId, scenarioId, jobId));
    }

    @PostMapping("/simulations/{jobId}/retry")
    @Operation(summary = "Repeat a failed or interrupted simulation of a scenario")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> retry(@PathVariable UUID projectId, @PathVariable UUID scenarioId, @PathVariable UUID jobId) {
        return Responses.accepted("Economic job queued", service.retry(projectId, scenarioId, jobId));
    }

    @GetMapping("/evaluations")
    @Operation(summary = "List a scenario's saved results (evaluations), newest first")
    public ResponseEntity<ApiResponse<List<EvaluationDTO>>> evaluations(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Economic evaluations", service.evaluations(projectId, scenarioId));
    }

    @GetMapping("/evaluations/{evaluationId}")
    @Operation(summary = "Read one saved result of a scenario", description = "404 when the evaluation belongs to another scenario.")
    public ResponseEntity<ApiResponse<EvaluationDTO>> evaluation(@PathVariable UUID projectId, @PathVariable UUID scenarioId, @PathVariable UUID evaluationId) {
        return Responses.ok("Economic evaluation", service.evaluation(projectId, scenarioId, evaluationId));
    }

    @GetMapping("/evaluations/{evaluationId}/rounds")
    @Operation(summary = "Round summaries of a probabilistic result")
    public ResponseEntity<ApiResponse<List<EconomicRoundSummary>>> rounds(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                                          @PathVariable UUID evaluationId) {
        return Responses.ok("Economic rounds", service.rounds(projectId, scenarioId, evaluationId));
    }

    @GetMapping("/evaluations/{evaluationId}/rounds/{round}")
    @Operation(summary = "One round of a probabilistic result")
    public ResponseEntity<ApiResponse<EconomicRoundResult>> round(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                                  @PathVariable UUID evaluationId, @PathVariable int round) {
        return Responses.ok("Economic round", service.round(projectId, scenarioId, evaluationId, round));
    }
}
