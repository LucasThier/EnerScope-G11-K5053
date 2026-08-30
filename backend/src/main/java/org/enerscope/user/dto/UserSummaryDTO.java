package org.enerscope.user.dto;

import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;

import java.util.UUID;

/**
 * Public projection of a {@link User}: identity plus platform role, with no
 * credential material. Returned by the auth endpoints so the client can render
 * the current user and gate UI by role without decoding the JWT.
 */
public record UserSummaryDTO(
        UUID id,
        String mail,
        String firstName,
        String lastName,
        PlatformRole platformRole
) {
    public static UserSummaryDTO from(User user) {
        return new UserSummaryDTO(
                user.getId(),
                user.getMail(),
                user.getFirstName(),
                user.getLastName(),
                user.getPlatformRole());
    }
}
