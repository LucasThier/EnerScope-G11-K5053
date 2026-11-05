package org.enerscope.economic.controller;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.dto.*;
import org.enerscope.economic.model.results.EconomicRoundResult;
import org.enerscope.economic.service.EconomicJobService;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/versions/{versionId}/economics")
public class EconomicJobController {
    private final EconomicJobService service;

    @PostMapping("/evaluation-jobs")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> submit(@PathVariable UUID projectId,@PathVariable UUID versionId,
            @RequestBody(required=false) EconomicJobRequest request) {
        return Responses.accepted("Economic job queued",service.submit(projectId,versionId,request));
    }

    @GetMapping("/evaluation-jobs")
    public ResponseEntity<ApiResponse<List<EconomicJobDTO>>> list(@PathVariable UUID projectId,@PathVariable UUID versionId) {
        return Responses.ok("Economic jobs",service.list(projectId,versionId));
    }

    @GetMapping("/evaluation-jobs/{jobId}")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> get(@PathVariable UUID projectId,@PathVariable UUID versionId,@PathVariable UUID jobId) {
        return Responses.ok("Economic job",service.get(projectId,versionId,jobId));
    }

    @PostMapping("/evaluation-jobs/{jobId}/retry")
    public ResponseEntity<ApiResponse<EconomicJobDTO>> retry(@PathVariable UUID projectId,@PathVariable UUID versionId,@PathVariable UUID jobId) {
        return Responses.accepted("Economic job queued",service.retry(projectId,versionId,jobId));
    }

    @GetMapping("/evaluations/{evaluationId}/rounds")
    public ResponseEntity<ApiResponse<List<EconomicRoundSummary>>> rounds(@PathVariable UUID projectId,@PathVariable UUID versionId,@PathVariable UUID evaluationId) {
        return Responses.ok("Economic rounds",service.roundSummaries(projectId,versionId,evaluationId));
    }

    @GetMapping("/evaluations/{evaluationId}/rounds/{round}")
    public ResponseEntity<ApiResponse<EconomicRoundResult>> round(@PathVariable UUID projectId,@PathVariable UUID versionId,
            @PathVariable UUID evaluationId,@PathVariable int round) {
        return Responses.ok("Economic round",service.round(projectId,versionId,evaluationId,round));
    }
}
