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

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(mock(AppLogger.class));

    @Test
    void uploadAboveTheLimitAnswers413WithAClearMessage() {
        var response = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1024 * 1024));

        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Uploaded file exceeds the maximum allowed size", response.getBody().message());
    }

    @Test
    void missingMultipartFieldReturns400WithFieldName() {
        var response = handler.handleMissingRequestPart(
                new MissingServletRequestPartException("file"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Required multipart field 'file' is missing", response.getBody().message());
    }

    @Test
    void malformedMultipartRequestReturns400() {
        var response = handler.handleMalformedMultipart(new MultipartException("malformed"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertFalse(response.getBody().success());
        assertEquals("Malformed multipart request", response.getBody().message());
    }

    @org.springframework.web.bind.annotation.RestController
    static class BrokenGraphController {
        @org.springframework.web.bind.annotation.GetMapping("/simulate")
        void simulate() {
            throw new org.enerscope.simulator.InvalidSimulationGraphException(
                    org.enerscope.simulator.InvalidSimulationGraphException.INVALID_CONNECTION,
                    new ClassCastException("internal detail"));
        }
    }

    @Test
    void anInvalidSimulationGraphAnswers400WithItsReasonAndLogsItsCause() throws Exception {
        AppLogger logger = mock(AppLogger.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(new BrokenGraphController())
                .setControllerAdvice(new GlobalExceptionHandler(logger)).build();

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/simulate"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.message")
                        .value("The value chain graph has a connection the simulator cannot process"));

        org.mockito.Mockito.verify(logger).warn("Rejected a simulation: {} (cause: {})",
                "The value chain graph has a connection the simulator cannot process", "ClassCastException");
    }
}
