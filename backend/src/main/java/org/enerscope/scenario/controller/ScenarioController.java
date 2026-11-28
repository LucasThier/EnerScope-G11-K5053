package org.enerscope.scenario.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.scenario.dto.ScenarioDTO;
import org.enerscope.scenario.dto.ScenarioNameRequest;
import org.enerscope.scenario.service.ScenarioService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Scenarios: the economic and simulation configuration that rests on a version (the physical base).
 * A version holds several; creating one never creates a version. Every route falls through to
 * {@code anyRequest().authenticated()}; permissions are checked in the service (403, then 404, then 403 on the
 * version, then 400, then 409).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}")
@Tag(name = "Scenarios", description = "Economic and simulation scenarios that rest on a version")
public class ScenarioController {
    private final ScenarioService service;

    @GetMapping("/versions/{versionId}/scenarios")
    @Operation(summary = "List the scenarios of a version",
            description = "Active scenarios of the version, oldest first. Any project member can read them (VIEW_PROJECT).")
    public ResponseEntity<ApiResponse<List<ScenarioDTO>>> list(@PathVariable UUID projectId, @PathVariable UUID versionId) {
        return Responses.ok("Scenarios", service.list(projectId, versionId));
    }

    @PostMapping("/versions/{versionId}/scenarios")
    @Operation(summary = "Create an empty scenario in a version",
            description = "The name is required and typed by the person; it must be unique in the version ignoring case "
                    + "(409) and a version holds at most 20 scenarios (409). Needs EDIT_PROJECT and the right to edit the version. "
                    + "It creates a scenario, never a version.")
    public ResponseEntity<ApiResponse<ScenarioDTO>> create(@PathVariable UUID projectId, @PathVariable UUID versionId,
                                                          @RequestBody(required = false) ScenarioNameRequest request) {
        return Responses.created("Scenario created", service.create(projectId, versionId, request));
    }

    @GetMapping("/scenarios/{scenarioId}")
    @Operation(summary = "Read a scenario",
            description = "404 (generic) when it does not exist, is deleted or belongs to another project.")
    public ResponseEntity<ApiResponse<ScenarioDTO>> get(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Scenario", service.get(projectId, scenarioId));
    }

    @PatchMapping("/scenarios/{scenarioId}")
    @Operation(summary = "Rename a scenario",
            description = "Needs EDIT_PROJECT and the right to edit its version. 409 when the name is already used in the version.")
    public ResponseEntity<ApiResponse<ScenarioDTO>> rename(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                          @RequestBody(required = false) ScenarioNameRequest request) {
        return Responses.ok("Scenario renamed", service.rename(projectId, scenarioId, request));
    }

    @PostMapping("/scenarios/{scenarioId}/duplicate")
    @Operation(summary = "Duplicate a scenario",
            description = "Copies only the published economic configuration into a new scenario of the same version, "
                    + "with the name the person gives; the source is left untouched and no draft or simulation is copied.")
    public ResponseEntity<ApiResponse<ScenarioDTO>> duplicate(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                             @RequestBody(required = false) ScenarioNameRequest request) {
        return Responses.created("Scenario duplicated", service.duplicate(projectId, scenarioId, request));
    }

    @DeleteMapping("/scenarios/{scenarioId}")
    @Operation(summary = "Delete a scenario (logical)",
            description = "Only whoever administers the version. Its evaluations, jobs and the comparisons that used it are kept. "
                    + "409 for the last scenario of the version or while a simulation is queued or running. "
                    + "Deleting the default promotes the oldest remaining scenario.")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        service.delete(projectId, scenarioId);
        return Responses.ok("Scenario deleted");
    }
}
