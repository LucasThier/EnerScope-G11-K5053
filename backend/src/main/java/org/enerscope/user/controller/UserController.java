package org.enerscope.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.user.dto.UpdatePlatformRoleRequestDTO;
import org.enerscope.user.dto.UserRoleDTO;
import org.enerscope.user.dto.UserSummaryDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.service.UserRoleService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
