package org.enerscope.common;

import org.enerscope.logging.AppLogger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler exceptionHandler =
            new GlobalExceptionHandler(mock(AppLogger.class));

    @Test
    void oversizedMultipartUploadReturns413WithApiResponse() {
        var response = exceptionHandler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Uploaded file exceeds the maximum allowed size", response.getBody().message());
    }

    @Test
    void missingMultipartFieldReturns400WithFieldName() {
        var response = exceptionHandler.handleMissingRequestPart(
                new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Required multipart field 'file' is missing", response.getBody().message());
    }

    @Test
    void malformedMultipartRequestReturns400() {
        var response = exceptionHandler.handleMalformedMultipart(new MultipartException("malformed"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Malformed multipart request", response.getBody().message());
    }
}
