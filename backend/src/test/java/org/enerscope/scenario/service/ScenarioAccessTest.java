package org.enerscope.scenario.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.ForbiddenException;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.enerscope.project.model.enums.ProjectMemberPermission.EDIT_PROJECT;
import static org.enerscope.project.model.enums.ProjectMemberPermission.VIEW_PROJECT;

/** The order of the answers when a scenario is addressed on its own: 403, 404, 403 on the version. */
class ScenarioAccessTest {
    final EconomicService economics = mock(EconomicService.class);
    final ScenarioRepository scenarios = mock(ScenarioRepository.class);
    final VersionAccessGuard guard = mock(VersionAccessGuard.class);
    final ScenarioAccess access = new ScenarioAccess(economics, scenarios, guard);

    final UUID projectId = UUID.randomUUID();
    final Project project = new Project("P", "d", null);
    final Version version = withId(new Version("V", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
    final Scenario scenario = withId(new Scenario(version, "Base", true));

    static <T> T withId(T entity) {
        ReflectionTestUtils.setField(entity, "id", UUID.randomUUID());
        return entity;
    }

    ScenarioAccessTest() {
        project.addVersion(version);
        when(economics.authorizeProject(eq(projectId), any())).thenReturn(project);
        when(scenarios.findById(scenario.getId())).thenReturn(Optional.of(scenario));
    }

    @Test
    void withoutTheProjectPermissionTheAnswerIs403AndNothingIsLookedUp() {
        when(economics.authorizeProject(projectId, VIEW_PROJECT)).thenThrow(new ForbiddenException("Project permission required"));

        assertThrows(ForbiddenException.class, () -> access.authorizeView(projectId, UUID.randomUUID()));
        verifyNoInteractions(scenarios, guard);
    }

    @Test
    void anUnknownInactiveOrForeignScenarioIsTheSameGeneric404BeforeTheVersionGuard() {
        Scenario inactive = withId(new Scenario(version, "Old", false));
        inactive.deactivate();
        when(scenarios.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        Version foreignVersion = withId(new Version("Other", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>()));
        Scenario foreign = withId(new Scenario(foreignVersion, "Foreign", true));
        when(scenarios.findById(foreign.getId())).thenReturn(Optional.of(foreign));

        for (UUID id : new UUID[] {UUID.randomUUID(), inactive.getId(), foreign.getId()}) {
            var error = assertThrows(EntityNotFoundException.class, () -> access.authorizeEdit(projectId, id));
            assertEquals("Scenario not found", error.getMessage());
        }
        verifyNoInteractions(guard);
    }

    @Test
    void aScenarioOfAnInactiveVersionIs404() {
        version.deactivate();

        assertThrows(EntityNotFoundException.class, () -> access.authorizeView(projectId, scenario.getId()));
    }

    @Test
    void readingNeedsOnlyTheProjectPermission() {
        var resolved = access.authorizeView(projectId, scenario.getId());

        assertSame(scenario, resolved.scenario());
        assertSame(version, resolved.version());
        verify(economics).authorizeProject(projectId, VIEW_PROJECT);
        verifyNoInteractions(guard);
    }

    @Test
    void editingNeedsEditProjectThenTheRightToEditTheVersion() {
        access.authorizeEdit(projectId, scenario.getId());

        var order = inOrder(economics, guard);
        order.verify(economics).authorizeProject(projectId, EDIT_PROJECT);
        order.verify(guard).assertCanEditVersion(version.getId());
    }

    @Test
    void deletingNeedsToAdministerTheVersionAndSimulatingToSimulateIt() {
        access.authorizeManage(projectId, scenario.getId());
        access.authorizeSimulate(projectId, scenario.getId());

        verify(guard).assertCanManageVersion(version.getId());
        verify(guard).assertCanSimulateVersion(version.getId());
        verify(guard, never()).assertCanEditVersion(any());
    }

    @Test
    void aRefusedVersionCapabilityIsA403AfterTheScenarioWasFound() {
        doThrow(new ForbiddenException("You are not allowed to manage this version")).when(guard).assertCanManageVersion(version.getId());

        assertThrows(ForbiddenException.class, () -> access.authorizeManage(projectId, scenario.getId()));
        verify(scenarios).findById(scenario.getId());
    }
}
