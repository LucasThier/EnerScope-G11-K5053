package org.enerscope.user.dto;

/**
 * A single row from a bulk-registration file that could not be registered,
 * along with the reason. Passwords are never included here.
 *
 * @param line   1-based line number in the uploaded file (header counts as 1)
 * @param mail   the email from the row (may be blank if the row had none)
 * @param reason human-readable explanation of why the row was rejected
 */
public record FailedRegistrationDTO(int line, String mail, String reason) {}
