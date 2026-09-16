package org.enerscope.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.session.model.Session;
import org.enerscope.user.dto.ChangePasswordRequestDTO;
import org.enerscope.user.service.UserService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.AuthUtil;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
