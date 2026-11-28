package org.enerscope.scenario.service;

import java.sql.SQLException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.common.UnauthorizedException;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.TransactionException;

/**
 * Decides which unexpected failures may be contained to the one scenario being checked. A list of scenarios reports
 * such a scenario as not ready and carries on; anything that is not about that scenario's data must still fail the
 * whole request.
 */
public final class ScenarioCheckFailures {

    /** The reason shown for a scenario whose check failed unexpectedly. The detail goes to the log only. */
    public static final String NOT_CHECKED = "The scenario could not be checked; review the server log";

    private ScenarioCheckFailures() {}

    /** Rethrows {@code failure} when it, or anything that caused it, is about the database, a transaction or access. */
    public static void rethrowUnlessScenarioSpecific(RuntimeException failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof DataAccessException || cause instanceof TransactionException
                    || cause instanceof jakarta.persistence.PersistenceException || cause instanceof SQLException
                    || cause instanceof ForbiddenException || cause instanceof UnauthorizedException
                    || cause instanceof AccessDeniedException) {
                throw failure;
            }
            if (cause.getCause() == cause) {
                return;
            }
        }
    }
}
