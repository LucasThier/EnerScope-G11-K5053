package org.enerscope.scenario.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.comparison.service.ScenarioStatusService;
import org.enerscope.economic.model.EconomicConfigurationEntity;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.repository.EconomicConfigurationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.scenario.dto.ScenarioDTO;
import org.enerscope.scenario.dto.ScenarioNameRequest;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.repository.EconomicDraftRepository;
import org.enerscope.scenario.repository.ScenarioDraftRepository;
import org.enerscope.scenario.repository.ScenarioRepository;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The scenarios of a version: list, create (empty), read, rename, duplicate and delete.
 *
 * <p>Every method answers in the same order: {@code 403} (project permission), {@code 404} (the version or scenario
 * does not exist, is inactive or belongs to another project — one generic message), {@code 403} (capability on the
 * version), {@code 400} (the name), {@code 409} (repeated name, limit reached, deletion refused). The limit and the
 * uniqueness of the name are checked while the version's write lock is held ({@code economics.lock}), so two
 * concurrent creations cannot both pass; the unique index on the name is the last safety net.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ScenarioService {
    private static final List<EconomicJobStatus> RUNNING_STATES = List.of(EconomicJobStatus.QUEUED, EconomicJobStatus.RUNNING);

    private final ScenarioAccess access;
    private final EconomicService economics;
    private final ScenarioRepository scenarios;
    private final EconomicConfigurationRepository configurations;
    private final ScenarioDraftRepository scenarioDrafts;
    private final EconomicDraftRepository legacyDrafts;
    private final EconomicJobRepository jobs;
    private final ScenarioChangeScope changeScope;
    private final ScenarioStatusService statuses;
    private final ScenarioLimits limits;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    /** The active scenarios of a version, oldest first. Needs {@code VIEW_PROJECT}. */
    @Transactional(readOnly = true)
    public List<ScenarioDTO> list(UUID projectId, UUID versionId) {
        Version version = economics.authorize(projectId, versionId, ProjectMemberPermission.VIEW_PROJECT);
        return dtos(scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(version.getId()));
    }

    @Transactional(readOnly = true)
    public ScenarioDTO get(UUID projectId, UUID scenarioId) {
        return dtos(List.of(access.authorizeView(projectId, scenarioId).scenario())).getFirst();
    }

    /** Creates an empty scenario (no configuration yet). The first scenario of a version without a default becomes it. */
    public ScenarioDTO create(UUID projectId, UUID versionId, ScenarioNameRequest request) {
        Version version = economics.authorizeConfigurationEdit(projectId, versionId);
        String name = cleanName(request);
        economics.lock(version);
        ensureRoom(version, name);
        boolean first = scenarios.findByVersionIdAndDefaultScenarioTrueAndActiveTrue(version.getId()).isEmpty();
        Scenario created = scenarios.saveAndFlush(new Scenario(version, name, first));
        logger.info("Scenario {} created in version {}", created.getId(), version.getId());
        return dtos(List.of(created)).getFirst();
    }

    public ScenarioDTO rename(UUID projectId, UUID scenarioId, ScenarioNameRequest request) {
        var resolved = access.authorizeEdit(projectId, scenarioId);
        String name = cleanName(request);
        economics.lock(resolved.version());
        Scenario scenario = resolved.scenario();
        if (scenarios.existsByVersionIdAndNameIgnoreCaseAndActiveTrueAndIdNot(resolved.version().getId(), name, scenario.getId())) {
            throw new ScenarioNameTakenException(name);
        }
        scenario.rename(name);
        scenarios.saveAndFlush(scenario);
        logger.info("Scenario {} renamed", scenario.getId());
        return dtos(List.of(scenario)).getFirst();
    }

    /**
     * Copies the published economic configuration of a scenario into a new one with the given name, in the same
     * version. Nothing else is copied (no draft, no simulations) and the source is left untouched. Copying rules about
     * the nodes of a vertical needs the right to edit that vertical, as saving them would.
     */
    public ScenarioDTO duplicate(UUID projectId, UUID scenarioId, ScenarioNameRequest request) {
        var resolved = access.authorizeEdit(projectId, scenarioId);
        Version version = resolved.version();
        Optional<EconomicConfigurationEntity> source = configurations.findByScenarioId(resolved.scenario().getId());
        source.ifPresent(stored -> changeScope.assertCanSave(version, null, null, read(stored.getConfigurationJson())));
        String name = cleanName(request);
        economics.lock(version);
        ensureRoom(version, name);
        Scenario copy = scenarios.saveAndFlush(new Scenario(version, name, false));
        source.ifPresent(stored -> {
            EconomicConfigurationEntity entity = new EconomicConfigurationEntity(version, stored.getConfigurationJson());
            entity.assignScenario(copy);
            configurations.save(entity);
        });
        logger.info("Scenario {} duplicated from {} in version {}", copy.getId(), resolved.scenario().getId(), version.getId());
        return dtos(List.of(copy)).getFirst();
    }

    /**
     * Logical delete ({@code active = false}): its evaluations, jobs and the comparisons that used it stay as they are.
     * Refused (409) for the last scenario of the version and while a simulation is queued or running. Deleting the default
     * promotes the oldest remaining one, removing the old mark first and setting the new one after, in this transaction.
     */
    public void delete(UUID projectId, UUID scenarioId) {
        var resolved = access.authorizeManage(projectId, scenarioId);
        Scenario scenario = resolved.scenario();
        economics.lock(resolved.version());
        List<Scenario> active = scenarios.findByVersionIdAndActiveTrueOrderByCreatedAtAscIdAsc(resolved.version().getId());
        if (active.size() <= 1) throw ScenarioDeletionRefusedException.lastScenario();
        if (jobs.existsByScenarioIdAndStatusIn(scenario.getId(), RUNNING_STATES)) throw ScenarioDeletionRefusedException.simulationInProgress();
        boolean wasDefault = scenario.isDefaultScenario();
        scenario.clearDefault();
        scenario.deactivate();
        scenarios.saveAndFlush(scenario);
        if (wasDefault) {
            Scenario next = active.stream().filter(candidate -> !candidate.getId().equals(scenario.getId())).findFirst().orElseThrow();
            next.markDefault();
            scenarios.saveAndFlush(next);
            logger.info("Scenario {} is now the default of version {}", next.getId(), resolved.version().getId());
        }
        logger.info("Scenario {} deleted from version {}", scenario.getId(), resolved.version().getId());
    }

    // ---------------------------------------------------------------- internals

    /** 400 for a missing, blank or too long name; the name is trimmed. */
    static String cleanName(ScenarioNameRequest request) {
        String name = request == null || request.name() == null ? "" : request.name().trim();
        if (name.isEmpty()) throw new IllegalArgumentException("El nombre del escenario es obligatorio.");
        if (name.length() > Scenario.NAME_MAX) {
            throw new IllegalArgumentException("El nombre del escenario admite hasta " + Scenario.NAME_MAX + " caracteres.");
        }
        return name;
    }

    /** 409 when the name is taken in the version (ignoring case) or the version already holds the maximum. */
    private void ensureRoom(Version version, String name) {
        if (scenarios.existsByVersionIdAndNameIgnoreCaseAndActiveTrue(version.getId(), name)) throw new ScenarioNameTakenException(name);
        if (scenarios.countByVersionIdAndActiveTrue(version.getId()) >= limits.maxPerVersion()) {
            throw new ScenarioLimitReachedException(limits.maxPerVersion());
        }
    }

    private List<ScenarioDTO> dtos(List<Scenario> list) {
        if (list.isEmpty()) return List.of();
        List<UUID> ids = list.stream().map(BaseEntity::getId).toList();
        Set<UUID> configured = new HashSet<>(configurations.scenarioIdsWithConfiguration(ids));
        Set<UUID> pending = new HashSet<>(scenarioDrafts.scenarioIdsWithDraft(ids));
        pending.addAll(legacyDrafts.scenarioIdsWithDraft(ids));
        return list.stream().map(s -> {
            var standing = statuses.standingOf(s.getVersion(), s);
            return new ScenarioDTO(s.getId(), s.getVersion().getId(), s.getName(), s.isDefaultScenario(), configured.contains(s.getId()),
                    pending.contains(s.getId()), standing.status(), standing.latestEvaluatedAt(), s.getCreatedAt(), s.getLastModified());
        }).toList();
    }

    private EconomicConfiguration read(String json) {
        try {
            return mapper.readValue(json, EconomicConfiguration.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot read the stored economic configuration", error);
        }
    }
}
