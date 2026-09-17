package org.enerscope.economic.controller;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.EconomicConfiguration;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/versions/{versionId}/economics")
public class EconomicController {
    private final EconomicService service;
    @PutMapping("/configuration")
    public ResponseEntity<ApiResponse<EconomicConfiguration>> save(@PathVariable UUID projectId, @PathVariable UUID versionId,
            @RequestBody EconomicConfiguration configuration) {
        return Responses.ok("Economic configuration saved", service.save(projectId, versionId, configuration));
    }
    @GetMapping("/configuration")
    public ResponseEntity<ApiResponse<EconomicConfiguration>> get(@PathVariable UUID projectId, @PathVariable UUID versionId) {
        return Responses.ok("Economic configuration", service.get(projectId, versionId));
    }
    @PostMapping("/evaluations")
    public ResponseEntity<ApiResponse<EvaluationDTO>> evaluate(@PathVariable UUID projectId, @PathVariable UUID versionId) {
        return Responses.created("Economic evaluation completed", service.evaluate(projectId, versionId));
    }
    @GetMapping("/evaluations")
    public ResponseEntity<ApiResponse<List<EvaluationDTO>>> list(@PathVariable UUID projectId, @PathVariable UUID versionId) {
        return Responses.ok("Economic evaluations", service.list(projectId, versionId));
    }
    @GetMapping("/evaluations/{evaluationId}")
    public ResponseEntity<ApiResponse<EvaluationDTO>> evaluation(@PathVariable UUID projectId, @PathVariable UUID versionId,
            @PathVariable UUID evaluationId) {
        return Responses.ok("Economic evaluation", service.getEvaluation(projectId, versionId, evaluationId));
    }
}
