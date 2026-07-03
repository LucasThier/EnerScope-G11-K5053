package org.enerscope.logging;

/**
 * Logging abstraction for the whole application.
 *
 * <p>Application code should depend on this interface instead of a concrete
 * logging framework. Swapping the logging strategy (e.g. structured JSON,
 * a remote log collector, a file appender) only requires providing a new
 * {@code AppLogger} implementation — no call site needs to change.</p>
 *
 * <p>{@code {}} placeholders in the message are replaced positionally by the
 * provided arguments, following the SLF4J convention.</p>
 */
public interface AppLogger {

    void debug(String message, Object... args);

    void info(String message, Object... args);

    void warn(String message, Object... args);

    void error(String message, Object... args);

    void error(String message, Throwable throwable);
}
