package org.enerscope.user.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.enerscope.common.BaseEntity;

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

    public User(String mail, String firstName, String lastName, String passwordHash) {
        this.mail = normalizeMail(mail);
        this.firstName = firstName;
        this.lastName = lastName;
        this.passwordHash = passwordHash;
    }

    /**
     * Rebuilds a detached {@link User} from JWT claims. The resulting instance
     * is never persisted; it only carries identity for the security context.
     */
    public static User fromJwtClaims(UUID id, String mail, String firstName, String lastName) {
        User user = new User(mail, firstName, lastName, "");
        user.id = id;
        return user;
    }

    public void updatePasswordHash(String newHash) {
        this.passwordHash = newHash;
    }

    private static String normalizeMail(String mail) {
        return mail == null ? null : mail.trim().toLowerCase(Locale.ROOT);
    }
}
