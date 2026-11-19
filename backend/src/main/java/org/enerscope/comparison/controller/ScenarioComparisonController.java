package org.enerscope.comparison.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.comparison.dto.ComparisonRequestDTO;
import org.enerscope.comparison.dto.ComparisonResultDTO;
import org.enerscope.comparison.dto.ComparisonSummaryDTO;
import org.enerscope.comparison.dto.SavedComparisonDTO;
import org.enerscope.comparison.dto.ComparableScenarioDTO;
import org.enerscope.comparison.service.ScenarioComparisonService;
import org.enerscope.comparison.service.ScenarioStatusService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Compares economic evaluations (results) of the scenarios of one project. Every
 * route falls through to {@code anyRequest().authenticated()} in
 * {@code SecurityConfig}; project permissions are checked in the service.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/comparisons")
@Tag(name = "Scenario comparison", description = "Compare the results of two or more scenarios of a project")
public class ScenarioComparisonController {

    private final ScenarioComparisonService service;
    private final ScenarioStatusService statuses;

    @GetMapping("/scenarios")
    @Operation(summary = "Scenarios that can be compared",
            description = "Every scenario of the project with the status of its latest simulation "
                    + "(up to date, outdated, never simulated, simulating) and whether the caller can simulate it.")
    public ResponseEntity<ApiResponse<List<ComparableScenarioDTO>>> scenarios(@PathVariable UUID projectId) {
        return Responses.ok("Comparable scenarios", statuses.list(projectId));
    }

    @PostMapping("/preview")
    @Operation(summary = "Compare evaluations without saving",
            description = "Two to " + ScenarioComparisonService.MAX_SCENARIOS + " scenarios from any project the caller "
                    + "can see, compared with each other. Each one uses its latest simulation, which must be up to date.")
    public ResponseEntity<ApiResponse<ComparisonResultDTO>> preview(@PathVariable UUID projectId,
                                                                    @RequestBody ComparisonRequestDTO request) {
        return Responses.ok("Comparison calculated", service.preview(projectId, request));
    }

    @PostMapping
    @Operation(summary = "Run and save a comparison",
            description = "Compares like /preview, stores which simulations were compared and returns the result. "
                    + "Without a name, it is named after its scenarios.")
    public ResponseEntity<ApiResponse<SavedComparisonDTO>> save(@PathVariable UUID projectId,
                                                                @RequestBody ComparisonRequestDTO request) {
        return Responses.created("Comparison saved", service.save(projectId, request));
    }

    @GetMapping
    @Operation(summary = "List saved comparisons")
    public ResponseEntity<ApiResponse<List<ComparisonSummaryDTO>>> list(@PathVariable UUID projectId) {
        return Responses.ok("Comparisons", service.list(projectId));
    }

    @GetMapping("/{comparisonId}")
    @Operation(summary = "Read a saved comparison", description = "Recalculated from the immutable evaluations it compares.")
    public ResponseEntity<ApiResponse<SavedComparisonDTO>> get(@PathVariable UUID projectId,
                                                               @PathVariable UUID comparisonId) {
        return Responses.ok("Comparison", service.get(projectId, comparisonId));
    }

    @DeleteMapping("/{comparisonId}")
    @Operation(summary = "Delete a saved comparison")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID projectId, @PathVariable UUID comparisonId) {
        service.delete(projectId, comparisonId);
        return Responses.ok("Comparison deleted");
    }
}
