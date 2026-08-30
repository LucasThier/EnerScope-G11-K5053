package org.enerscope.version.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.enerscope.version.dto.CreateVersionRequestDTO;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Version-management endpoints. These are not under {@code /auth/**}, so they
 * require a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/projects/{projectId}/versions")
@Tag(name = "Versions", description = "Project version management operations")
public class VersionController {

    private final VersionService versionService;

    public VersionController(VersionService versionService) {
        this.versionService = versionService;
    }

    @PostMapping
    @Operation(summary = "Create a version",
            description = "Create a new version of a project, optionally derived from a parent version.")
    public ResponseEntity<ApiResponse<VersionDTO>> createVersion(
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateVersionRequestDTO data) {
        Version version = versionService.createVersion(projectId, data);
        return Responses.created("Version created", toDTO(version));
    }

    private VersionDTO toDTO(Version version) {
        return new VersionDTO(
                version.getId(),
                version.getName(),
                version.getCreatedAt(),
                version.getProject().getId(),
                version.getParentVersion() != null ? version.getParentVersion().getId() : null);
    }
}
