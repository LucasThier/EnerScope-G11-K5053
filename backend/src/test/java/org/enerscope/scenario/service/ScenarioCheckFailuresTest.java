package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.UUID;
import org.enerscope.common.ForbiddenException;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.logging.AppLogger;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.CannotCreateTransactionException;

/** What may be contained to one scenario of a list, and what must still fail the request. */
@ExtendWith(MockitoExtension.class)
class ScenarioCheckFailuresTest {

    @Mock EconomicService economics;
    @Mock ScenarioDraftRepository drafts;
    @Mock EconomicDraftRepository legacyDrafts;
    @Mock AppLogger logger;
    @InjectMocks ScenarioConfigurationService service;

    @Test
    void infrastructureAndAccessFailuresAreRethrownEvenWhenWrapped() {
        for (RuntimeException failure : new RuntimeException[] {
                new DataAccessResourceFailureException("connection lost"),
                new CannotCreateTransactionException("no connection"),
                new jakarta.persistence.PersistenceException("flush failed"),
                new IllegalStateException(new SQLException("broken pipe")),
                new RuntimeException(new DataAccessResourceFailureException("wrapped")),
                new ForbiddenException("not allowed")}) {
            assertSame(failure, assertThrows(RuntimeException.class,
                    () -> ScenarioCheckFailures.rethrowUnlessScenarioSpecific(failure)));
        }
    }

    @Test
    void aFailureAboutTheScenariosDataIsContained() {
        assertDoesNotThrow(() -> ScenarioCheckFailures.rethrowUnlessScenarioSpecific(
                new IllegalStateException("Cannot read economic snapshot", new java.io.IOException("bad json"))));
        assertDoesNotThrow(() -> ScenarioCheckFailures.rethrowUnlessScenarioSpecific(new NullPointerException()));
    }

    @Test
    void readinessDoesNotHideADatabaseFailure() {
        var failure = new DataAccessResourceFailureException("connection lost");
        Scenario scenario = mock(Scenario.class);
        when(economics.configuration(scenario)).thenThrow(failure);

        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> service.readinessOf(mock(Version.class), scenario)));
    }

    @Test
    void readinessReportsAndLogsAnUnforeseenFailureOfOneScenario() {
        var failure = new IllegalStateException("unreadable");
        Scenario scenario = mock(Scenario.class);
        Version version = mock(Version.class);
        UUID scenarioId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        when(scenario.getId()).thenReturn(scenarioId);
        when(version.getId()).thenReturn(versionId);
        when(economics.configuration(scenario)).thenThrow(failure);

        var readiness = service.readinessOf(version, scenario);

        assertFalse(readiness.ready());
        assertEquals(java.util.List.of(ScenarioCheckFailures.NOT_CHECKED), readiness.errors());
        verify(logger).error("Readiness of scenario {} of version {} could not be checked",
                scenarioId, versionId, failure);
    }
}
