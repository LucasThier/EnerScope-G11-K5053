package org.enerscope.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Default {@link AppLogger} implementation. Writes to the console through
 * SLF4J (the logging facade Spring Boot ships with Logback for).
 *
 * <p>This is the only place in the codebase that is aware of the concrete
 * logging framework. To change the logging destination, provide a different
 * {@link AppLogger} bean and mark this one as a fallback (or remove it).</p>
 */
@Component
public class ConsoleAppLogger implements AppLogger {

    private static final Logger log = LoggerFactory.getLogger("EnerScope");

    @Override
    public void debug(String message, Object... args) {
        log.debug(message, args);
    }

    @Override
    public void info(String message, Object... args) {
        log.info(message, args);
    }

    @Override
    public void warn(String message, Object... args) {
        log.warn(message, args);
    }

    @Override
    public void error(String message, Object... args) {
        log.error(message, args);
    }

    @Override
    public void error(String message, Throwable throwable) {
        log.error(message, throwable);
    }
}
