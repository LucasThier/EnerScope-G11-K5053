package org.enerscope.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.user.dto.AssignRoleRequestDTO;
import org.enerscope.user.dto.RemoveRoleRequestDTO;
import org.enerscope.user.dto.UpdatePlatformRoleRequestDTO;
import org.enerscope.user.dto.UserRoleDTO;
import org.enerscope.user.dto.UserSummaryDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.service.UserRoleService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * User role endpoints. These are not under {@code /auth/**}, so they require
 * a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User role management operations")
public class UserController {

    private final UserRoleService userRoleService;

    public UserController(UserRoleService userRoleService) {
        this.userRoleService = userRoleService;
    }

    @GetMapping("/{userId}/roles")
    @Operation(summary = "List a user's roles",
            description = "Every role the user holds across platform, organization and project levels. "
                    + "Allowed for a platform admin, or for the user looking up their own roles.")
    public ResponseEntity<ApiResponse<List<UserRoleDTO>>> listRoles(@PathVariable UUID userId) {
        return Responses.ok("User roles", userRoleService.listRoles(userId));
    }

    @PatchMapping("/{userId}/platform-role")
    @Operation(summary = "Assign a user's platform role",
            description = "Set a user's platform-wide role (ADMIN or USER). Only a platform admin may call this.")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> assignPlatformRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdatePlatformRoleRequestDTO data) {
        User updated = userRoleService.assignPlatformRole(userId, data.role());
        return Responses.ok("Platform role updated", UserSummaryDTO.from(updated));
    }

    @PostMapping("/{userId}/roles")
    @Operation(summary = "Assign a role to a user at any level",
            description = "Assign a role at the platform, organization or project level. For "
                    + "ORGANIZATION/PROJECT, resourceId is the organization/project id and roleType is "
                    + "that level's member type (e.g. OWNER/MEMBER, ADMIN/EDITOR); for PLATFORM, "
                    + "resourceId must be omitted and roleType is ADMIN or USER. Each level enforces its "
                    + "own authorization rules (see OrganizationService/ProjectService/assignPlatformRole).")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequestDTO data) {
        userRoleService.assignRole(userId, data.level(), data.resourceId(), data.roleType());
        return Responses.created("Role assigned");
    }

    @DeleteMapping("/{userId}/roles")
    @Operation(summary = "Remove a user's role at any level",
            description = "Remove a role at the organization or project level (resourceId required). "
                    + "The platform role cannot be removed this way — assign a different one instead.")
    public ResponseEntity<Void> removeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RemoveRoleRequestDTO data) {
        userRoleService.removeRole(userId, data.level(), data.resourceId());
        return ResponseEntity.noContent().build();
    }
}
