package org.enerscope.util;

import org.enerscope.session.model.Session;
import org.springframework.security.core.context.SecurityContextHolder;

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
}
