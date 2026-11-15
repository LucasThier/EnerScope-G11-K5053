package org.enerscope.common;

import org.enerscope.logging.AppLogger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AppLogger.class));

    @Test
    void uploadAboveTheLimitAnswers413WithAClearMessage() {
        var response = handler.handleUploadTooLarge(new MaxUploadSizeExceededException(1024 * 1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("The uploaded file exceeds the maximum size of 1 MB", response.getBody().message());
    }
}
