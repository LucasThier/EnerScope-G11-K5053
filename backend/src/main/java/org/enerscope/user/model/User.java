package org.enerscope.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.user.model.enums.PlatformRole;

import java.util.Locale;
import java.util.UUID;

@NoArgsConstructor
@Getter
@Entity
@Table(
        name = "app_user",
        indexes = @Index(name = "idx_app_user_mail", columnList = "mail")
)
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String mail;

    @Column(name = "first_name", nullable = false, length = 60)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 60)
    private String lastName;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_role", nullable = false, length = 20)
    private PlatformRole platformRole;

    public User(String mail, String firstName, String lastName, String passwordHash, PlatformRole platformRole) {
        this.mail = normalizeMail(mail);
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
        this.platformRole = platformRole;
    }

    /** Convenience constructor for a regular ({@link PlatformRole#USER}) account. */
    public User(String mail, String firstName, String lastName, String passwordHash) {
        this(mail, firstName, lastName, passwordHash, PlatformRole.USER);
    }

    /**
     * Rebuilds a detached {@link User} from JWT claims. The resulting instance
     * is never persisted; it only carries identity for the security context.
     */
    public static User fromJwtClaims(UUID id, String mail, String firstName, String lastName, PlatformRole platformRole) {
        User user = new User(mail, firstName, lastName, "", platformRole);
        user.id = id;
        return user;
    }

    /** Overload defaulting to {@link PlatformRole#USER} when no role is provided. */
    public static User fromJwtClaims(UUID id, String mail, String firstName, String lastName) {
        return fromJwtClaims(id, mail, firstName, lastName, PlatformRole.USER);
    }

    public void updatePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    public void updatePlatformRole(PlatformRole newRole) {
        this.platformRole = newRole;
    }

    private static String normalizeMail(String mail) {
        return mail == null ? null : mail.trim().toLowerCase(Locale.ROOT);
    }
}
