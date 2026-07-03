package org.enerscope.logging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ConsoleAppLoggerTest {

    private final AppLogger logger = new ConsoleAppLogger();

    @Test
    void logsAtEveryLevelWithoutThrowing() {
        assertDoesNotThrow(() -> {
            logger.debug("debug {}", 1);
            logger.info("info {}", "value");
            logger.warn("warn");
            logger.error("error {}", "boom");
            logger.error("error with cause", new RuntimeException("cause"));
        });
    }
}
