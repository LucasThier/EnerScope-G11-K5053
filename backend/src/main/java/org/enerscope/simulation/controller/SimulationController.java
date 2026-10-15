package org.enerscope.simulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.simulation.dto.CostEstimateDTO;
import org.enerscope.simulation.dto.RunSimulationRequestDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.service.SimulationService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Simulation runs of a version, and the live cost estimate of its model. Nested
 * under {@code /version/{versionId}} because a run belongs to exactly one version
 * and is authorized through it; {@code SecurityConfig} needs no entry, these fall
 * through to {@code anyRequest().authenticated()}.
 */
@RestController
@RequestMapping("/version/{versionId}")
@Tag(name = "Simulations", description = "Run the simulation engine on a version and read its results")
public class SimulationController {

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @PostMapping(value = "/simulations", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Simulate a version",
            description = "Runs the Monte Carlo simulation of the version's current model over the given horizon "
                    + "and stores the run, with the model's cost over the same horizon. "
                    + "Requires EDIT_PROJECT on the owning project.")
    public ResponseEntity<ApiResponse<SimulationDetailDTO>> runSimulation(
            @PathVariable UUID versionId,
            @Valid @RequestBody RunSimulationRequestDTO request) {
        return Responses.created("Simulation completed", simulationService.runSimulation(versionId, request));
    }

    @GetMapping("/simulations")
    @Operation(summary = "List the simulations of a version", description = "Newest first, with headline figures.")
    public ResponseEntity<ApiResponse<List<SimulationSummaryDTO>>> listSimulations(@PathVariable UUID versionId) {
        return Responses.ok("Simulations retrieved", simulationService.listSimulations(versionId));
    }

    @GetMapping("/simulations/{simulationId}")
    @Operation(summary = "Get a simulation", description = "A run with its breakdown by vertical and by node.")
    public ResponseEntity<ApiResponse<SimulationDetailDTO>> getSimulation(
            @PathVariable UUID versionId,
            @PathVariable UUID simulationId) {
        return Responses.ok("Simulation retrieved", simulationService.getSimulation(versionId, simulationId));
    }

    @DeleteMapping("/simulations/{simulationId}")
    @Operation(summary = "Delete a simulation", description = "Requires EDIT_PROJECT on the owning project.")
    public ResponseEntity<ApiResponse<Void>> deleteSimulation(
            @PathVariable UUID versionId,
            @PathVariable UUID simulationId) {
        simulationService.deleteSimulation(versionId, simulationId);
        return Responses.ok("Simulation deleted");
    }

    @GetMapping("/costs")
    @Operation(summary = "Estimate the cost of a version",
            description = "CAPEX, OPEX and upkeep of the version's current model over the given horizon, "
                    + "computed without simulating.")
    public ResponseEntity<ApiResponse<CostEstimateDTO>> estimateCosts(
            @PathVariable UUID versionId,
            @RequestParam(defaultValue = "20") int years) {
        return Responses.ok("Cost estimate", simulationService.estimateCosts(versionId, years));
    }
}
