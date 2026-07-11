package org.enerscope.user.dto;

import java.util.List;

/**
 * Outcome of a bulk-registration request.
 *
 * <p>{@code credentialsCsv} contains the generated {@code mail,password} rows
 * (with a header) for every successfully created user, ready to be saved as a
 * {@code .csv} file and distributed. Failed rows are reported separately in
 * {@code failures} and never carry a password.</p>
 *
 * @param total          number of data rows found in the uploaded file
 * @param created        number of users successfully registered
 * @param failed         number of rows rejected
 * @param credentialsCsv CSV text with {@code mail,password} for created users
 * @param failures       details for each rejected row
 */
public record BulkRegistrationResultDTO(
        int total,
        int created,
        int failed,
        String credentialsCsv,
        List<FailedRegistrationDTO> failures
) {}
