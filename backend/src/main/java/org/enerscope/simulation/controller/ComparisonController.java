package org.enerscope.simulation.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.enerscope.simulation.dto.VersionComparisonDTO;
import org.enerscope.simulation.service.VersionComparisonService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Compares versions of a project. Lives under {@code /projects/{projectId}}
 * because the project is what authorizes the read and what every compared
 * version must belong to. Falls through to {@code anyRequest().authenticated()}
 * in {@code SecurityConfig}.
 */
@RestController
@RequestMapping("/projects/{projectId}/comparison")
@Tag(name = "Comparison", description = "Compare the versions (scenarios) of a project")
public class ComparisonController {

    private final VersionComparisonService comparisonService;

    public ComparisonController(VersionComparisonService comparisonService) {
        this.comparisonService = comparisonService;
    }

    @GetMapping
    @Operation(summary = "Compare versions",
            description = "Two to " + VersionComparisonService.MAX_VERSIONS + " versions of the project, the first "
                    + "one being the baseline: their structure, the changes against the baseline and the figures "
                    + "of each version's latest simulation. Pass versionIds=a,b,c in order.")
    public ResponseEntity<ApiResponse<VersionComparisonDTO>> compare(
            @PathVariable UUID projectId,
            // Optional at the binding level so a missing list reaches the service and
            // answers its own 400 ("pick at least two") instead of a binding error.
            @RequestParam(required = false) List<UUID> versionIds) {
        return Responses.ok("Comparison", comparisonService.compare(projectId, versionIds));
    }
}
