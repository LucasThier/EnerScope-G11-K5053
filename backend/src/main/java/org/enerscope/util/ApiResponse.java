package org.enerscope.util;

import java.time.Instant;

public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        Instant timestamp) {

    public static <T> ApiResponse<T> ok(String msg, T data) {
        return new ApiResponse<>(true, msg, data, Instant.now());
    }

    public static <T> ApiResponse<T> ok(String msg) {
        return new ApiResponse<>(true, msg, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String msg) {
        return new ApiResponse<>(false, msg, null, Instant.now());
    }

    public static <T> ApiResponse<T> error(String msg, T data) {
        return new ApiResponse<>(false, msg, data, Instant.now());
    }
}
