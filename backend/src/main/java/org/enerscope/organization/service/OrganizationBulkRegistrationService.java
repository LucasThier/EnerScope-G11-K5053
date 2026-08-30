package org.enerscope.organization.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.CsvUtil;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.BulkRegistrationResultDTO;
import org.enerscope.organization.dto.FailedRegistrationDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.service.PasswordGenerator;
import org.enerscope.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Registers many users at once from an uploaded CSV, adding each one to a single
 * organization as a member.
 *
 * <p>The file must have a header row with columns for the email, first name and
 * last name; an optional {@code role} column sets the membership type
 * ({@code OWNER}/{@code MEMBER}, default {@code MEMBER}). Column names are matched
 * case-insensitively with a few aliases. For each valid row a strong password is
 * generated, the account is created as a regular platform user and the
 * membership is added; the plaintext passwords are returned as a
 * {@code mail,password} CSV so the operator can distribute them.</p>
 *
 * <p>Only a platform admin or an organization owner (a member with
 * {@code MANAGE_ORGANIZATION}) may call this — the check runs once up front.
 * Invalid or duplicate rows do not abort the batch: they are collected as
 * failures and reported back alongside the successes.</p>
 */
@Service
public class OrganizationBulkRegistrationService {

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
    private static final Set<String> ROLE_HEADERS = Set.of(
            "role", "rol", "membertype", "member_type", "member type", "tipo");

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final PasswordGenerator passwordGenerator;
    private final AppLogger logger;

    public OrganizationBulkRegistrationService(OrganizationRepository organizationRepository,
                                               OrganizationMemberRepository organizationMemberRepository,
                                               OrganizationService organizationService,
                                               UserService userService,
                                               PasswordGenerator passwordGenerator,
                                               AppLogger logger) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.passwordGenerator = passwordGenerator;
        this.logger = logger;
    }

    public BulkRegistrationResultDTO register(UUID organizationId, String csvContent) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        organizationService.assertCanManageUsers(organizationId);

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
            String roleRaw = columns.role != null ? at(row, columns.role) : null;

            String error = validate(mail, firstName, lastName, roleRaw, seenMails);
            if (error != null) {
                failures.add(new FailedRegistrationDTO(line, mail, error));
                continue;
            }

            try {
                OrganizationMemberType memberType = parseMemberType(roleRaw);
                String password = passwordGenerator.generate();
                User user = userService.register(new RegisterRequestDTO(
                        mail, firstName, lastName, password, PlatformRole.USER));
                addMembership(organization, user, memberType);
                credentials.add(List.of(mail.trim().toLowerCase(Locale.ROOT), password));
            } catch (IllegalArgumentException ex) {
                failures.add(new FailedRegistrationDTO(line, mail, ex.getMessage()));
            }
        }

        int created = credentials.size() - 1; // minus the header row
        logger.info("Bulk registration for organization {} processed {} rows: {} created, {} failed",
                organization.getName(), total, created, failures.size());

        return new BulkRegistrationResultDTO(
                total, created, failures.size(), CsvUtil.write(credentials), failures);
    }

    private void addMembership(Organization organization, User user, OrganizationMemberType memberType) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(
                memberType.name(), memberType, OrganizationService.defaultPermissionsFor(memberType)));
        organization.addMember(member);
        organizationMemberRepository.save(member);
    }

    private String validate(String mail, String firstName, String lastName,
                            String roleRaw, Set<String> seenMails) {
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
        if (invalidRole(roleRaw)) {
            return "Invalid role (OWNER or MEMBER)";
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

    private boolean invalidRole(String roleRaw) {
        if (roleRaw == null || roleRaw.isBlank()) {
            return false; // optional: no role means the default MEMBER
        }
        try {
            OrganizationMemberType.valueOf(roleRaw.trim().toUpperCase(Locale.ROOT));
            return false;
        } catch (IllegalArgumentException ex) {
            return true;
        }
    }

    private OrganizationMemberType parseMemberType(String roleRaw) {
        if (roleRaw == null || roleRaw.isBlank()) {
            return OrganizationMemberType.MEMBER;
        }
        return OrganizationMemberType.valueOf(roleRaw.trim().toUpperCase(Locale.ROOT));
    }

    private Columns resolveColumns(List<String> header) {
        Integer mail = null;
        Integer firstName = null;
        Integer lastName = null;
        Integer role = null;
        for (int i = 0; i < header.size(); i++) {
            String key = header.get(i).trim().toLowerCase(Locale.ROOT);
            if (mail == null && MAIL_HEADERS.containsKey(key)) {
                mail = i;
            } else if (firstName == null && FIRST_NAME_HEADERS.containsKey(key)) {
                firstName = i;
            } else if (lastName == null && LAST_NAME_HEADERS.containsKey(key)) {
                lastName = i;
            } else if (role == null && ROLE_HEADERS.contains(key)) {
                role = i;
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
        return new Columns(mail, firstName, lastName, role);
    }

    private static String at(List<String> row, int index) {
        return index < row.size() ? row.get(index) : null;
    }

    private record Columns(int mail, int firstName, int lastName, Integer role) {}
}
