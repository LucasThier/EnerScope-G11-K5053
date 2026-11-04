package org.enerscope.scenario.controller;
import lombok.RequiredArgsConstructor;
import org.enerscope.scenario.service.ScenarioRevisionService;
import org.enerscope.scenario.service.ScenarioRevisionService.RevisionSummary;
import org.enerscope.scenario.service.ScenarioRevisionService.RevisionDetail;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/scenarios/{scenarioId}/revisions")
public class ScenarioRevisionController {
    private final ScenarioRevisionService service;
    @GetMapping
    public ResponseEntity<ApiResponse<List<RevisionSummary>>> list(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Scenario revisions", service.list(projectId, scenarioId));
    }
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<ScenarioRevisionService.RevisionStatus>> status(@PathVariable UUID projectId, @PathVariable UUID scenarioId) {
        return Responses.ok("Scenario revision status", service.status(projectId, scenarioId));
    }
    public record RestoreRequest(boolean replacePending) {}
    @PostMapping("/{revisionId}/restore")
    public ResponseEntity<ApiResponse<ScenarioRevisionService.RevisionStatus>> restore(@PathVariable UUID projectId,
            @PathVariable UUID scenarioId,@PathVariable UUID revisionId,@RequestHeader("If-Match") String token,
            @RequestBody RestoreRequest request) {
        return Responses.ok("Revision restored into draft",service.restore(projectId,scenarioId,revisionId,token,request.replacePending()));
    }
    @GetMapping("/{revisionId}")
    public ResponseEntity<ApiResponse<RevisionDetail>> get(@PathVariable UUID projectId, @PathVariable UUID scenarioId,
                                                          @PathVariable UUID revisionId) {
        return Responses.ok("Scenario revision", service.get(projectId, scenarioId, revisionId));
    }
}
