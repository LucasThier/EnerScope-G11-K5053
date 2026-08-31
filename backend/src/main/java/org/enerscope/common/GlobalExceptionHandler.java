package org.enerscope.common;

import org.enerscope.logging.AppLogger;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private final AppLogger logger;

    public GlobalExceptionHandler(AppLogger logger) {
        this.logger = logger;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        e -> e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value",
                        (m1, m2) -> m1));
        return Responses.badRequest("Validation error", errors);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return Responses.unauthorized(ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiResponse<Void>> handleForbidden(ForbiddenException ex) {
        return Responses.forbidden(ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        return Responses.badRequest(ex.getMessage());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound() {
        return Responses.notFound("Resource not found");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotAllowed() {
        return Responses.badRequest("Method not allowed");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return Responses.badRequest("Invalid argument for parameter '" + ex.getName() + "'");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        Throwable cause = ex.getCause();

        if (cause instanceof InvalidFormatException ife) {
            String field = ife.getPath().stream()
                    .map(JsonMappingException.Reference::getFieldName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("."));

            Class<?> targetType = ife.getTargetType();
            String message;

            if (targetType.isEnum()) {
                String validValues = Arrays.stream(targetType.getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                message = "Invalid value '%s' for field '%s'. Allowed values: [%s]"
                        .formatted(ife.getValue(), field, validValues);
            } else {
                message = "Invalid value '%s' for field '%s'. Expected type: %s"
                        .formatted(ife.getValue(), field, targetType.getSimpleName());
            }

            return Responses.badRequest(message);
        }

        return Responses.badRequest("Malformed request body");
    }

    @ExceptionHandler(VersionNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleVersionNotFound(VersionNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAny(Exception ex) {
        logger.error("Unhandled internal error", ex);
        return Responses.serverError("Internal error");
    }

}
