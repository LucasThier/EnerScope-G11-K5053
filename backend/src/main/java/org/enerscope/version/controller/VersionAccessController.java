package org.enerscope.version.controller;

import lombok.RequiredArgsConstructor;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.enerscope.version.dto.ProjectAccessDTO;
import org.enerscope.version.service.VersionAccessService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/projects/{projectId}/access")
public class VersionAccessController {

    private final VersionAccessService service;

    @GetMapping
    public ResponseEntity<ApiResponse<ProjectAccessDTO>> access(@PathVariable UUID projectId) {
        return Responses.ok("Project access", service.resolveForProject(projectId));
    }
}
