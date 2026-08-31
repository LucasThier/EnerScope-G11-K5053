package org.enerscope.version.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
<<<<<<< HEAD
import lombok.AllArgsConstructor;

import java.util.UUID;

import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/version")
@Tag(name = "Versions", description = "Version management operations")
@AllArgsConstructor
public class VersionController {

    private final VersionService versionService;
    /*
     * create a new version.
     */

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a new version", description = "Create a new Version with all required properties.")
    public ResponseEntity<ApiResponse<Version>> createVersion(
            @RequestBody VersionDTO versionDTO) {
        Version version = versionService.saveVersion(versionDTO);
        return Responses.ok("Version created successfully", version);
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a version", description = "Delete an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Void>> deleteVersion(
            @PathVariable UUID id) {
        versionService.deleteVersion(id);
        return Responses.ok("Version deleted successfully");
    }

    @GetMapping(value = "/{id}")
    @Operation(summary = "Get a version", description = "Retrieve an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> getVersion(
            @PathVariable UUID id) {
        Version version = versionService.getVersion(id);

        return Responses.ok("Version retrieved successfully", version);
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modify a version", description = "Modify an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> modifyVersion(
            @PathVariable UUID id,
            @RequestBody VersionDTO versionDTO) {
        Version version = versionService.modifyVersion(id, versionDTO);

        return Responses.ok("Version modified successfully", version);
    }

    @PostMapping(value = "/{versionId}/node", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add node to version", description = "Add a node to a version")
    public ResponseEntity<ApiResponse<BaseNode>> addNodeToVersion(
            @PathVariable UUID versionId,
            @RequestBody BaseNodeDTO nodeDTO) {
        BaseNode result = versionService.addNodeToVersion(versionId, nodeDTO);
        return Responses.ok("Node added to version successfully", result);
    }

    @PostMapping(value = "/{versionId}/connection", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add connection to version", description = "Add a connection to a version")
    public ResponseEntity<ApiResponse<Object>> addConnectionToVersion(
            @PathVariable UUID versionId,
            @RequestBody ConnectionDTO connectionDTO) {
        Object result = versionService.addConnectionToVersion(versionId, connectionDTO);
        return Responses.ok("Connection added to version successfully", result);
    }

    @PatchMapping(value = "/{versionId}/node/{nodeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit node in version", description = "Edit a node in a version")
    public ResponseEntity<ApiResponse<BaseNode>> editNodeInVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId,
            @RequestBody BaseNodeDTO nodeDTO) {
        BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);
        return Responses.ok("Node edited in version successfully", result);
    }

    @PatchMapping(value = "/{versionId}/connection/{connectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit connection in version", description = "Edit a connection in a version")
    public ResponseEntity<ApiResponse<NodeConnection>> editConnectionInVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID connectionId,
            @RequestBody ConnectionDTO connectionDTO) {
        NodeConnection result = versionService.editConnectionInVersion(versionId, connectionId, connectionDTO);
        return Responses.ok("Connection edited in version successfully", result);
    }

    @DeleteMapping(value = "/{versionId}/node/{nodeId}")
    @Operation(summary = "Delete node from version", description = "Delete a node from a version")
    public ResponseEntity<ApiResponse<Void>> deleteNodeFromVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId) {
        versionService.deleteNodeFromVersion(versionId, nodeId);
        return Responses.ok("Node deleted from version successfully");
    }

    @DeleteMapping(value = "/{versionId}/connection/{connectionId}")
    @Operation(summary = "Delete connection from version", description = "Delete a connection from a version")
    public ResponseEntity<ApiResponse<Void>> deleteConnectionFromVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID connectionId) {
        versionService.deleteConnectionFromVersion(versionId, connectionId);
        return Responses.ok("Connection deleted from version successfully");
=======
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
>>>>>>> master
    }
}
