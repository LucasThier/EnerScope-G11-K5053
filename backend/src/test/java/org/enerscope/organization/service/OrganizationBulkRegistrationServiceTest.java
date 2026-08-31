package org.enerscope.organization.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.CsvUtil;
import org.enerscope.common.ForbiddenException;
import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.BulkRegistrationResultDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.user.service.PasswordGenerator;
import org.enerscope.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationBulkRegistrationServiceTest {

    private static final UUID ORG_ID = UUID.randomUUID();

    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private OrganizationMemberRepository organizationMemberRepository;
    @Mock
    private OrganizationService organizationService;
    @Mock
    private UserService userService;
    @Mock
    private PasswordGenerator passwordGenerator;
    @Mock
    private AppLogger logger;

    private OrganizationBulkRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new OrganizationBulkRegistrationService(
                organizationRepository, organizationMemberRepository,
                organizationService, userService, passwordGenerator, logger);
        lenient().when(organizationRepository.findById(any())).thenReturn(Optional.of(new Organization("Acme")));
        lenient().when(passwordGenerator.generate()).thenReturn("Str0ng!Pass-01");
        lenient().when(userService.register(any(RegisterRequestDTO.class)))
                .thenReturn(new User("row@enerscope.org", "Row", "User", "hash", PlatformRole.USER));
        lenient().when(organizationMemberRepository.save(any(OrganizationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void registersValidRowsAndAddsThemAsMembers() {
        String csv = "mail,firstName,lastName\n"
                + "Jane@Example.com,Jane,Doe\n"
                + "john@example.com,John,Roe\n";

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(2, result.total());
        assertEquals(2, result.created());
        assertEquals(0, result.failed());
        verify(userService, times(2)).register(any(RegisterRequestDTO.class));
        verify(organizationMemberRepository, times(2)).save(any(OrganizationMember.class));

        List<List<String>> credentials = CsvUtil.parse(result.credentialsCsv());
        assertEquals(List.of("mail", "password"), credentials.get(0));
        assertEquals(List.of("jane@example.com", "Str0ng!Pass-01"), credentials.get(1));
    }

    @Test
    void defaultsToMemberWhenNoRoleColumn() {
        String csv = "mail,firstName,lastName\njane@example.com,Jane,Doe\n";

        service.register(ORG_ID, csv);

        assertEquals(OrganizationMemberType.MEMBER, savedMemberType());
    }

    @Test
    void assignsMemberTypeFromRoleColumn() {
        String csv = "mail,firstName,lastName,role\nboss@example.com,Boss,Lee,OWNER\n";

        service.register(ORG_ID, csv);

        assertEquals(OrganizationMemberType.OWNER, savedMemberType());
    }

    @Test
    void rejectsInvalidRoleValueAsFailure() {
        String csv = "mail,firstName,lastName,role\nx@example.com,Ann,Lee,BOSS\n";

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(0, result.created());
        assertEquals(1, result.failed());
        assertTrue(result.failures().get(0).reason().contains("role"));
        verify(userService, never()).register(any());
    }

    @Test
    void collectsInvalidRowsAsFailuresWithoutAborting() {
        String csv = "mail,firstName,lastName\n"
                + "good@example.com,Ann,Lee\n"
                + ",No,Mail\n"
                + "not-an-email,Bad,Email\n"
                + "short@example.com,A,Ok\n";

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(4, result.total());
        assertEquals(1, result.created());
        assertEquals(3, result.failed());
        assertEquals("Missing email", result.failures().get(0).reason());
        assertEquals("Invalid email", result.failures().get(1).reason());
        assertTrue(result.failures().get(2).reason().contains("first name"));
    }

    @Test
    void rejectsDuplicateEmailWithinFile() {
        String csv = "mail,firstName,lastName\n"
                + "dup@example.com,First,One\n"
                + "DUP@example.com,Second,Two\n";

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(1, result.created());
        assertEquals(1, result.failed());
        assertEquals("Duplicate email in file", result.failures().get(0).reason());
    }

    @Test
    void propagatesRegistrationFailuresPerRow() {
        String csv = "mail,firstName,lastName\ntaken@example.com,Al,Ready\n";
        when(userService.register(any()))
                .thenThrow(new IllegalArgumentException("An account with that email already exists"));

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(0, result.created());
        assertEquals(1, result.failed());
        assertEquals("An account with that email already exists", result.failures().get(0).reason());
    }

    @Test
    void acceptsHeaderAliasesAndAnyColumnOrder() {
        String csv = "Apellido,Nombre,Email\nDoe,Jane,jane@example.com\n";

        BulkRegistrationResultDTO result = service.register(ORG_ID, csv);

        assertEquals(1, result.created());
        List<List<String>> credentials = CsvUtil.parse(result.credentialsCsv());
        assertEquals("jane@example.com", credentials.get(1).get(0));
    }

    @Test
    void throwsWhenRequiredHeaderColumnMissing() {
        String csv = "mail,firstName\njane@example.com,Jane\n";
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.register(ORG_ID, csv));
        assertTrue(ex.getMessage().contains("lastName"));
        verify(userService, never()).register(any());
    }

    @Test
    void throwsWhenFileIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.register(ORG_ID, ""));
    }

    @Test
    void rejectsUnknownOrganization() {
        when(organizationRepository.findById(ORG_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.register(ORG_ID, "mail,firstName,lastName\nj@e.com,Jane,Doe\n"));
        verify(userService, never()).register(any());
    }

    @Test
    void rejectsWhenCallerNotAuthorized() {
        doThrow(new ForbiddenException("nope")).when(organizationService).assertCanManageUsers(ORG_ID);

        assertThrows(ForbiddenException.class,
                () -> service.register(ORG_ID, "mail,firstName,lastName\nj@e.com,Jane,Doe\n"));
        verify(userService, never()).register(any());
        verify(organizationMemberRepository, never()).save(any());
    }

    private OrganizationMemberType savedMemberType() {
        ArgumentCaptor<OrganizationMember> captor = ArgumentCaptor.forClass(OrganizationMember.class);
        verify(organizationMemberRepository).save(captor.capture());
        return captor.getValue().getRoles().iterator().next().getMemberType();
    }
}
