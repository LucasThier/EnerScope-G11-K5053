package org.enerscope.comparison.controller;

import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.comparison.dto.ComparisonDTO.*;
import org.enerscope.comparison.service.ComparisonService;
import org.enerscope.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/comparisons")
public class ComparisonController {
    private final ComparisonService service;

    @PostMapping("/preview")
    public ResponseEntity<ApiResponse<Result>> preview(@PathVariable UUID projectId, @RequestBody Request request) {
        return Responses.ok("Comparison calculated", service.preview(projectId, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Saved>> save(@PathVariable UUID projectId, @RequestBody Request request) {
        return Responses.created("Comparison saved", service.save(projectId, request));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<Summary>>> list(@PathVariable UUID projectId) {
        return Responses.ok("Comparisons", service.list(projectId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Saved>> get(@PathVariable UUID projectId, @PathVariable UUID id) {
        return Responses.ok("Comparison", service.get(projectId, id));
    }
}
