package org.enerscope.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.session.model.Session;
import org.enerscope.user.dto.ChangePasswordRequestDTO;
import org.enerscope.user.dto.UpdateProfileRequestDTO;
import org.enerscope.user.dto.UpdateRoleRequestDTO;
import org.enerscope.user.dto.UserDetailDTO;
import org.enerscope.user.dto.UserListItemDTO;
import org.enerscope.user.dto.UserSearchResultDTO;
import org.enerscope.user.dto.UserSummaryDTO;
import org.enerscope.user.model.User;
import org.enerscope.user.service.UserService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.AuthUtil;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Operations a user performs on their own account. Not under {@code /auth/**},
 * so a valid Bearer token is required (see {@code SecurityConfig}, where
 * {@code /users/**} falls through to {@code anyRequest().authenticated()} —
 * the intended rule here, so no explicit matcher was added).
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Operations on the caller's own account")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @Operation(summary = "List every platform user",
            description = "Platform administrators only. Returns every account, deactivated ones "
                    + "included, with how many organizations each one belongs to. Accounts created "
                    + "outside an organization show zero and are reachable from nowhere else.")
    public ResponseEntity<ApiResponse<List<UserListItemDTO>>> listUsers() {
        return Responses.ok("Users", userService.listAll());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Get a user's full detail",
            description = "Platform administrators only. Identity plus every organization and "
                    + "project the user belongs to. A suspended account still answers with its "
                    + "detail — the admin needs to see exactly that account. Unknown id answers a "
                    + "fixed 404.")
    public ResponseEntity<ApiResponse<UserDetailDTO>> getUserDetail(@PathVariable UUID userId) {
        return Responses.ok("User", userService.getDetail(userId));
    }

    @GetMapping("/search")
    @Operation(summary = "Find one user by their exact email",
            description = "For platform admins and for owners of an active organization, so an "
                    + "owner can add somebody who already has an account. Exact match only, "
                    + "case-insensitive, and the projection carries nothing but the name and "
                    + "address. A suspended account answers the same 404 as an address that was "
                    + "never registered.")
    public ResponseEntity<ApiResponse<UserSearchResultDTO>> searchUser(@RequestParam String mail) {
        return Responses.ok("User", userService.searchByMail(mail));
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "Change a user's platform role",
            description = "Platform administrators only. Promoting grants the platform-admin "
                    + "bypass that every authorization check in the system honours. A demotion is "
                    + "refused when it would leave the platform with no active administrator.")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> updateRole(
            @PathVariable UUID userId,
            @Valid @RequestBody UpdateRoleRequestDTO data) {
        User user = userService.updateRole(userId, data.platformRole());
        return Responses.ok("Platform role updated", UserSummaryDTO.from(user));
    }

    @PostMapping("/{userId}/reactivate")
    @Operation(summary = "Reactivate a user",
            description = "Platform administrators only. Brings a deactivated account back: it can "
                    + "sign in again and its memberships stop showing as suspended. No last-admin "
                    + "check applies, since reactivating can only add an active administrator.")
    public ResponseEntity<ApiResponse<Void>> reactivateUser(@PathVariable UUID userId) {
        userService.reactivateUser(userId);
        return Responses.ok("User reactivated");
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Deactivate a user",
            description = "Platform administrators only. The account is deactivated, not deleted: "
                    + "it stops signing in and its organization and project memberships are kept, "
                    + "where it shows as suspended. Refused when it would leave the platform with "
                    + "no active administrator.")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID userId) {
        userService.deactivateUser(userId);
        return Responses.ok("User deleted");
    }

    @PatchMapping("/me")
    @Operation(summary = "Update your own profile",
            description = "Change your first name, last name or job title. Fields left out are kept "
                    + "as they are, and a blank job title clears it. The account comes from the "
                    + "session, so this endpoint cannot be pointed at another user.")
    public ResponseEntity<ApiResponse<UserSummaryDTO>> updateOwnProfile(
            @Valid @RequestBody UpdateProfileRequestDTO data) {
        Session session = AuthUtil.requireSession();
        User user = userService.updateProfile(session.getUser().getId(), data);
        return Responses.ok("Profile updated", UserSummaryDTO.from(user));
    }

    /**
     * Changes the caller's own password. The account is taken from the session,
     * never from the request, so this endpoint cannot be pointed at someone
     * else's account — an admin resetting another user's password would be a
     * different endpoint with its own authorization.
     */
    @PatchMapping("/me/password")
    @Operation(summary = "Change your own password",
            description = "Requires the current password. Accounts created by an admin or through "
                    + "bulk registration start with a password chosen by someone else; this is how "
                    + "their owner replaces it.")
    public ResponseEntity<ApiResponse<Void>> changeOwnPassword(
            @Valid @RequestBody ChangePasswordRequestDTO data) {
        Session session = AuthUtil.requireSession();
        userService.changePassword(session.getUser().getId(), data.currentPassword(), data.newPassword());
        return Responses.ok("Password changed");
    }
}
