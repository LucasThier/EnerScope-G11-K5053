package org.enerscope.version.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import java.util.UUID;

import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<ApiResponse<Void>> createVersion(
            @RequestBody VersionDTO versionDTO) {
        versionService.saveVersion(versionDTO);
        return Responses.ok("Version created successfully");
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a version", description = "Delete an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Void>> deleteVersion(
            @PathVariable UUID id) {
        versionService.deleteVersion(id);
        return Responses.ok("Version deleted successfully"); // Version not found donde lo meto?
    }

    @GetMapping(value = "/{id}")
    @Operation(summary = "Get a version", description = "Retrieve an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> getVersion(
            @PathVariable UUID id) {
        Version version = versionService.getVersion(id);

        return Responses.ok(version, "Version retrieved successfully"); // Version not found donde lo meto?
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modify a version", description = "Modify an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> modifyVersion(
            @RequestBody VersionDTO versionDTO) {
        versionService.modifyVersion(versionDTO);

        return Responses.ok("Version retrieved successfully"); // Version not found donde lo meto?
    }
}
