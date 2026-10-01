package org.enerscope.version.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.util.*;
import org.enerscope.version.dto.VersionSummaryDTO;
import org.enerscope.version.service.VersionQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/versions")
public class VersionQueryController {
    private final VersionQueryService service;

    @GetMapping
    public ResponseEntity<ApiResponse<List<VersionSummaryDTO>>> list(@PathVariable UUID projectId) {
        return Responses.ok("Versions", service.list(projectId));
    }
}
