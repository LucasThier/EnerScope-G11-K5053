package org.enerscope.util;

import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Access to the caller behind the current request, and the two checks every
 * service runs on it. The session-null check and the platform-ADMIN comparison
 * used to be copy-pasted across {@code OrganizationService},
 * {@code ProjectService} and {@code ProjectAccessGuard}; they live here so the
 * rule — and the message the caller reads — is written once.
 */
public final class AuthUtil {
    private AuthUtil() {}

    /**
     * Returns the {@link Session} bound to the current request, or {@code null}
     * when the request is unauthenticated. The session is attached to the
     * authentication details by the auth filter.
     */
    public static Session currentSession() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getDetails() instanceof Session s) {
            return s;
        }
        return null;
    }

    /**
     * The session behind the current request, or {@link UnauthorizedException}
     * (401) when there is none. Use this wherever an endpoint needs to know who
     * is calling — it is the 401 half of every authorization check.
     */
    public static Session requireSession() {
        Session session = currentSession();
        if (session == null) {
            throw new UnauthorizedException("Authentication required");
        }
        return session;
    }

    /**
     * Whether the caller is a platform ADMIN, which is the shortcut every
     * organization- and project-scoped check starts with. A predicate rather
     * than an assertion because callers use it to <em>skip</em> their own
     * membership lookup, not to reject.
     */
    public static boolean isPlatformAdmin(User caller) {
        return caller.getPlatformRole() == PlatformRole.ADMIN;
    }

    /**
     * Ensures the caller is a platform ADMIN, for actions no organization or
     * project membership can grant. Throws {@link UnauthorizedException} (401)
     * when there is no session and {@link ForbiddenException} (403) otherwise.
     *
     * <p>{@code action} completes the sentence the caller reads back — pass
     * {@code "create organizations"} to answer "Only platform admins can create
     * organizations". The {@link AppLogger} is a parameter because this is a
     * static helper and the logger is an injected bean; passing it keeps the
     * refusal logged at the call site's own level.</p>
     */
    public static void requirePlatformAdmin(AppLogger logger, String action) {
        User caller = requireSession().getUser();
        if (!isPlatformAdmin(caller)) {
            logger.warn("User {} is not a platform admin and may not {}", caller.getMail(), action);
            throw new ForbiddenException("Only platform admins can " + action);
        }
    }
}
