package org.enerscope.session;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import org.enerscope.user.User;

import java.time.Instant;
import java.util.Objects;

/**
 * In-memory representation of an authenticated request. JWT is stateless, so a
 * {@code Session} is rebuilt from the access token on every request rather than
 * being stored in a table.
 */
@Getter
public class Session {

    private final String token;

    private final User user;

    private final Instant expiresAt;

    public Session(@NotNull String token, User user, Instant expiresAt) {
        this.token = Objects.requireNonNull(token);
        this.user = Objects.requireNonNull(user);
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
