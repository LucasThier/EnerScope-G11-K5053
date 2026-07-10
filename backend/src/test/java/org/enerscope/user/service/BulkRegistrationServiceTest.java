package org.enerscope.user.service;

import org.enerscope.auth.dto.RegisterRequestDTO;
import org.enerscope.common.CsvUtil;
import org.enerscope.logging.AppLogger;
import org.enerscope.user.dto.BulkRegistrationResultDTO;
import org.enerscope.user.dto.FailedRegistrationDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BulkRegistrationServiceTest {

    @Mock
    private UserService userService;
    @Mock
    private PasswordGenerator passwordGenerator;
    @Mock
    private AppLogger logger;

    private BulkRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new BulkRegistrationService(userService, passwordGenerator, logger);
        lenient().when(passwordGenerator.generate()).thenReturn("Str0ng!Pass-01");
    }

    @Test
    void registersValidRowsAndReturnsCredentialsCsv() {
        String csv = "mail,firstName,lastName\n"
                + "Jane@Example.com,Jane,Doe\n"
                + "john@example.com,John,Roe\n";

        BulkRegistrationResultDTO result = service.register(csv);

        assertEquals(2, result.total());
        assertEquals(2, result.created());
        assertEquals(0, result.failed());
        verify(userService, org.mockito.Mockito.times(2)).register(any(RegisterRequestDTO.class));

        List<List<String>> credentials = CsvUtil.parse(result.credentialsCsv());
        assertEquals(List.of("mail", "password"), credentials.get(0));
        // Email is normalised to lower-case in the output.
        assertEquals(List.of("jane@example.com", "Str0ng!Pass-01"), credentials.get(1));
        assertEquals(List.of("john@example.com", "Str0ng!Pass-01"), credentials.get(2));
    }

    @Test
    void collectsInvalidRowsAsFailuresWithoutAborting() {
        String csv = "mail,firstName,lastName\n"
                + "good@example.com,Ann,Lee\n"
                + ",No,Mail\n"
                + "not-an-email,Bad,Email\n"
                + "short@example.com,A,Ok\n";

        BulkRegistrationResultDTO result = service.register(csv);

        assertEquals(4, result.total());
        assertEquals(1, result.created());
        assertEquals(3, result.failed());

        List<FailedRegistrationDTO> failures = result.failures();
        assertEquals(3, failures.get(0).line()); // ",No,Mail" is the 3rd file line
        assertEquals("Missing email", failures.get(0).reason());
        assertEquals("Invalid email", failures.get(1).reason());
        assertTrue(failures.get(2).reason().contains("first name"));
    }

    @Test
    void rejectsDuplicateEmailWithinFile() {
        String csv = "mail,firstName,lastName\n"
                + "dup@example.com,First,One\n"
                + "DUP@example.com,Second,Two\n";

        BulkRegistrationResultDTO result = service.register(csv);

        assertEquals(1, result.created());
        assertEquals(1, result.failed());
        assertEquals("Duplicate email in file", result.failures().get(0).reason());
        verify(userService, org.mockito.Mockito.times(1)).register(any());
    }

    @Test
    void propagatesRegistrationFailuresPerRow() {
        String csv = "mail,firstName,lastName\n"
                + "taken@example.com,Al,Ready\n";
        when(userService.register(any()))
                .thenThrow(new IllegalArgumentException("An account with that email already exists"));

        BulkRegistrationResultDTO result = service.register(csv);

        assertEquals(0, result.created());
        assertEquals(1, result.failed());
        assertEquals("An account with that email already exists",
                result.failures().get(0).reason());
    }

    @Test
    void acceptsHeaderAliasesAndAnyColumnOrder() {
        String csv = "Apellido,Nombre,Email\n"
                + "Doe,Jane,jane@example.com\n";

        BulkRegistrationResultDTO result = service.register(csv);

        assertEquals(1, result.created());
        List<List<String>> credentials = CsvUtil.parse(result.credentialsCsv());
        assertEquals("jane@example.com", credentials.get(1).get(0));
    }

    @Test
    void throwsWhenRequiredHeaderColumnMissing() {
        String csv = "mail,firstName\njane@example.com,Jane\n";
        IllegalArgumentException ex =
                assertThrows(IllegalArgumentException.class, () -> service.register(csv));
        assertTrue(ex.getMessage().contains("lastName"));
        verify(userService, never()).register(any());
    }

    @Test
    void throwsWhenFileIsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> service.register(""));
    }
}
