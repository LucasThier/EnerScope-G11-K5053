package org.enerscope.scenario.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves and authorizes a scenario addressed as {@code /projects/{projectId}/scenarios/{scenarioId}}.
 *
 * <p>Order of answers: <b>403</b> when the caller lacks the project permission (before anything is looked up),
 * then <b>404</b> — the same generic "Scenario not found" — when the scenario does not exist, is inactive, belongs
 * to an inactive version or to another project, then <b>403</b> when the caller lacks the capability on the
 * version. Validation (400) and rule conflicts (409) come later, in the services.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioAccess {
    /** The scenario and the version it rests on, both loaded in the current persistence context. */
    public record Resolved(Scenario scenario, Version version) {}

    private final EconomicService economics;
    private final ScenarioRepository scenarios;
    private final VersionAccessGuard versionGuard;

    /** Read access: the project permission only (every project member, the read-only Ejecutivo included). */
    public Resolved authorizeView(UUID projectId, UUID scenarioId) {
        return resolve(projectId, scenarioId, ProjectMemberPermission.VIEW_PROJECT);
    }

    /** Create/duplicate/rename/save the configuration: edit the version the scenario rests on. */
    public Resolved authorizeEdit(UUID projectId, UUID scenarioId) {
        Resolved resolved = resolve(projectId, scenarioId, ProjectMemberPermission.EDIT_PROJECT);
        versionGuard.assertCanEditVersion(resolved.version().getId());
        return resolved;
    }

    /** Delete: administer the version (version administrator, project administrator, platform administrator). */
    public Resolved authorizeManage(UUID projectId, UUID scenarioId) {
        Resolved resolved = resolve(projectId, scenarioId, ProjectMemberPermission.EDIT_PROJECT);
        versionGuard.assertCanManageVersion(resolved.version().getId());
        return resolved;
    }

    /** Run simulations of the scenario. */
    public Resolved authorizeSimulate(UUID projectId, UUID scenarioId) {
        Resolved resolved = resolve(projectId, scenarioId, ProjectMemberPermission.EDIT_PROJECT);
        versionGuard.assertCanSimulateVersion(resolved.version().getId());
        return resolved;
    }

    private Resolved resolve(UUID projectId, UUID scenarioId, ProjectMemberPermission permission) {
        var project = economics.authorizeProject(projectId, permission);
        Scenario scenario = scenarios.findById(scenarioId).filter(Scenario::isActive)
                .orElseThrow(() -> new EntityNotFoundException("Scenario not found"));
        UUID versionId = scenario.getVersion().getId();
        Version version = project.getVersions().stream().filter(v -> v.getId().equals(versionId) && v.isActive()).findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Scenario not found"));
        return new Resolved(scenario, version);
    }
}
