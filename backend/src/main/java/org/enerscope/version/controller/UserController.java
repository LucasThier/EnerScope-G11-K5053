package org.enerscope.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.enerscope.user.dto.BulkRegistrationResultDTO;
import org.enerscope.user.service.BulkRegistrationService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * User-management endpoints. These are not under {@code /auth/**}, so they
 * require a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "User management operations")
public class UserController {

    private final BulkRegistrationService bulkRegistrationService;

    public UserController(BulkRegistrationService bulkRegistrationService) {
        this.bulkRegistrationService = bulkRegistrationService;
    }

    @PostMapping(value = "/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Register many users from a CSV",
            description = "Upload a CSV with 'mail', 'firstName' and 'lastName' columns. "
                    + "Each valid row is created with a securely generated password. "
                    + "The response includes a 'mail,password' CSV to distribute.")
    public ResponseEntity<ApiResponse<BulkRegistrationResultDTO>> bulkRegister(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        BulkRegistrationResultDTO result = bulkRegistrationService.register(content);
        return Responses.ok(
                "Processed " + result.total() + " rows: "
                        + result.created() + " created, " + result.failed() + " failed",
                result);
    }
}
