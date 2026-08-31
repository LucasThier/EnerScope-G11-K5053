package org.enerscope.common;

/**
 * Thrown when the caller is authenticated but not allowed to perform the
 * requested action (mapped to HTTP 403 by {@code GlobalExceptionHandler}).
 */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
