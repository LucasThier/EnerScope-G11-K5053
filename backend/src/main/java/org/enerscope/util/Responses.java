package org.enerscope.util;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class Responses {
    private Responses() {}

    public static <T> ResponseEntity<ApiResponse<T>> ok(String msg, T data) {
        return ResponseEntity.ok(ApiResponse.ok(msg, data));
    }

    public static ResponseEntity<ApiResponse<Void>> ok(String msg) {
        return ResponseEntity.ok(ApiResponse.ok(msg));
    }

    public static <T> ResponseEntity<ApiResponse<T>> created(String msg, T data) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(msg, data));
    }

    public static ResponseEntity<ApiResponse<Void>> created(String msg) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(msg));
    }

    public static ResponseEntity<ApiResponse<Void>> badRequest(String msg) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg));
    }

    public static <T> ResponseEntity<ApiResponse<T>> badRequest(String msg, T data) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.error(msg, data));
    }

    public static ResponseEntity<ApiResponse<Void>> unauthorized(String msg) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(msg));
    }

    public static ResponseEntity<ApiResponse<Void>> forbidden(String msg) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.error(msg));
    }

    public static ResponseEntity<ApiResponse<Void>> notFound(String msg) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg));
    }

    public static <T> ResponseEntity<ApiResponse<T>> notFound(String msg, T data) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(msg, data));
    }

    public static ResponseEntity<ApiResponse<Void>> serverError(String msg) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(msg));
    }
}
