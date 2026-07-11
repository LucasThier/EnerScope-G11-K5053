package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.CsvUtil;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.dto.BulkRegistrationResultDTO;
import org.enerscope.user.dto.FailedRegistrationDTO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Registers many users at once from an uploaded CSV.
 *
 * <p>The file must have a header row and columns for the email, first name and
 * last name. Column names are matched case-insensitively and a few common
 * aliases are accepted ({@code email}, {@code first_name}/{@code nombre},
 * {@code last_name}/{@code apellido}). For each valid row a strong password is
 * generated and the user is created; the plaintext passwords are returned as a
 * {@code mail,password} CSV so the operator can distribute them.</p>
 *
 * <p>Invalid or duplicate rows do not abort the batch: they are collected as
 * failures and reported back alongside the successes.</p>
 */
@Service
public class BulkRegistrationService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final int NAME_MIN = 2;
    private static final int NAME_MAX = 60;

    // Header aliases -> canonical field. All keys are lower-case.
    private static final Map<String, String> MAIL_HEADERS = Map.of(
            "mail", "mail", "email", "mail", "e-mail", "mail", "correo", "mail");
    private static final Map<String, String> FIRST_NAME_HEADERS = Map.of(
            "firstname", "firstName", "first_name", "firstName",
            "first name", "firstName", "nombre", "firstName");
    private static final Map<String, String> LAST_NAME_HEADERS = Map.of(
            "lastname", "lastName", "last_name", "lastName",
            "last name", "lastName", "apellido", "lastName");

    private final UserService userService;
    private final PasswordGenerator passwordGenerator;
    private final AppLogger logger;

    public BulkRegistrationService(UserService userService,
                                   PasswordGenerator passwordGenerator,
                                   AppLogger logger) {
        this.userService = userService;
        this.passwordGenerator = passwordGenerator;
        this.logger = logger;
    }

    public BulkRegistrationResultDTO register(String csvContent) {
        List<List<String>> rows = CsvUtil.parse(csvContent);
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("The CSV file is empty");
        }

        Columns columns = resolveColumns(rows.get(0));

        List<List<String>> credentials = new ArrayList<>();
        credentials.add(List.of("mail", "password"));
        List<FailedRegistrationDTO> failures = new ArrayList<>();
        Set<String> seenMails = new HashSet<>();

        int total = 0;
        for (int i = 1; i < rows.size(); i++) {
            total++;
            List<String> row = rows.get(i);
            int line = i + 1; // 1-based, header is line 1

            String mail = at(row, columns.mail);
            String firstName = at(row, columns.firstName);
            String lastName = at(row, columns.lastName);

            String error = validate(mail, firstName, lastName, seenMails);
            if (error != null) {
                failures.add(new FailedRegistrationDTO(line, mail, error));
                continue;
            }

            try {
                String password = passwordGenerator.generate();
                userService.register(new RegisterRequestDTO(mail, firstName, lastName, password));
                credentials.add(List.of(mail.trim().toLowerCase(Locale.ROOT), password));
            } catch (IllegalArgumentException ex) {
                failures.add(new FailedRegistrationDTO(line, mail, ex.getMessage()));
            }
        }

        int created = credentials.size() - 1; // minus the header row
        logger.info("Bulk registration processed {} rows: {} created, {} failed",
                total, created, failures.size());

        return new BulkRegistrationResultDTO(
                total, created, failures.size(), CsvUtil.write(credentials), failures);
    }

    private String validate(String mail, String firstName, String lastName, Set<String> seenMails) {
        if (mail == null || mail.isBlank()) {
            return "Missing email";
        }
        if (!EMAIL.matcher(mail.trim()).matches()) {
            return "Invalid email";
        }
        if (!seenMails.add(mail.trim().toLowerCase(Locale.ROOT))) {
            return "Duplicate email in file";
        }
        if (invalidName(firstName)) {
            return "Invalid first name (2-60 characters required)";
        }
        if (invalidName(lastName)) {
            return "Invalid last name (2-60 characters required)";
        }
        return null;
    }

    private boolean invalidName(String name) {
        if (name == null) {
            return true;
        }
        String trimmed = name.trim();
        return trimmed.length() < NAME_MIN || trimmed.length() > NAME_MAX;
    }

    private Columns resolveColumns(List<String> header) {
        Integer mail = null;
        Integer firstName = null;
        Integer lastName = null;
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (mail == null && MAIL_HEADERS.containsKey(key)) {
                mail = i;
            } else if (firstName == null && FIRST_NAME_HEADERS.containsKey(key)) {
                firstName = i;
            } else if (lastName == null && LAST_NAME_HEADERS.containsKey(key)) {
                lastName = i;
            }
        }
        List<String> missing = new ArrayList<>();
        if (mail == null) missing.add("mail");
        if (firstName == null) missing.add("firstName");
        if (lastName == null) missing.add("lastName");
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV header is missing required column(s): " + String.join(", ", missing));
        }
        return new Columns(mail, firstName, lastName);
    }

    private static String at(List<String> row, int index) {
        return index < row.size() ? row.get(index) : null;
    }

    private record Columns(int mail, int firstName, int lastName) {}
}
