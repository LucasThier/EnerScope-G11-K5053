package org.enerscope.role.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.role.dto.RoleAssignmentRequestDTO;
import org.enerscope.role.dto.UserRoleDTO;
import org.enerscope.role.service.RoleService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Cross-level role management: assign, remove and list a user's roles across
 * the platform, organization and project levels in one place.
 */
@RestController
@RequestMapping("/users/{userId}/roles")
@Tag(name = "Roles", description = "Cross-level role management")
public class UserRoleController {

    private final RoleService roleService;

    public UserRoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @Operation(summary = "List a user's roles",
            description = "Every role the user holds across platform, organization and project "
                    + "levels. Callable by the user themself or a platform admin.")
    public ResponseEntity<ApiResponse<List<UserRoleDTO>>> listRoles(@PathVariable UUID userId) {
        return Responses.ok("User roles", roleService.listRoles(userId));
    }

    @PostMapping
    @Operation(summary = "Assign a role to a user",
            description = "Creates the organization/project membership if the user does not already "
                    + "have one. The caller needs manage authority at or above the target level.")
    public ResponseEntity<ApiResponse<Void>> assignRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleAssignmentRequestDTO data) {
        roleService.assignRole(userId, data);
        return Responses.created("Role assigned");
    }

    @DeleteMapping
    @Operation(summary = "Remove a role from a user",
            description = "Removes the role; if it was the user's last role on that resource, the "
                    + "membership itself is removed.")
    public ResponseEntity<ApiResponse<Void>> removeRole(
            @PathVariable UUID userId,
            @Valid @RequestBody RoleAssignmentRequestDTO data) {
        roleService.removeRole(userId, data);
        return Responses.ok("Role removed");
    }
}
