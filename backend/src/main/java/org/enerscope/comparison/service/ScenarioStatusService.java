package org.enerscope.comparison.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.BaseEntity;
import org.enerscope.common.EntityNotFoundException;
import org.enerscope.comparison.dto.ComparableScenarioDTO;
import org.enerscope.comparison.dto.ComparableScenarioDTO.SimulationJob;
import org.enerscope.comparison.model.enums.EvaluationMode;
import org.enerscope.comparison.model.enums.ScenarioStatus;
import org.enerscope.economic.dto.EvaluationDTO;
import org.enerscope.economic.model.EconomicEvaluation;
import org.enerscope.economic.model.EconomicJob;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.repository.EconomicEvaluationRepository;
import org.enerscope.economic.repository.EconomicJobRepository;
import org.enerscope.economic.service.EconomicInputCodec;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO.Readiness;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.scenario.model.Scenario;
import org.enerscope.scenario.service.ScenarioDefaults;
import org.enerscope.version.service.VersionAccessService;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tells, per scenario, whether its latest simulation still matches the scenario.
 *
 * <p>A simulation freezes the economic configuration, the physical nodes and the
 * connections it ran on. The scenario is {@link ScenarioStatus#UP_TO_DATE} only while
 * the current ones are the same; any change (a tariff, a well's capacity, a new node)
 * makes it {@link ScenarioStatus#OUTDATED}. Diagram positions and audit timestamps
 * are ignored: they never reach the simulator. Unpublished drafts do not count either —
 * they are not the scenario yet.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScenarioStatusService {

    /** Keys that change without changing what is simulated. */
    private static final Set<String> IGNORED_KEYS = Set.of("createdAt", "lastModified", "graphData");

    private final EconomicService economics;
    private final ScenarioConfigurationService scenarios;
    private final VersionAccessService access;
    private final EconomicInputCodec codec;
    private final EconomicEvaluationRepository evaluations;
    private final EconomicJobRepository jobs;
    private final ScenarioDefaults scenarioDefaults;
    private final org.enerscope.scenario.repository.ScenarioRepository scenarioRepository;
    private final ObjectMapper mapper;
    private final AppLogger logger;

    /**
     * Every active scenario of every active version of a project, grouped by version (versions oldest first, then their
     * scenarios oldest first), each with the status of its own latest simulation. Needs {@code VIEW_PROJECT}.
     */
    public List<ComparableScenarioDTO> list(UUID projectId) {
        Project project = economics.authorizeProject(projectId, ProjectMemberPermission.VIEW_PROJECT);
        // The same per-version rule that authorizes submitting a simulation (version roles, then project).
        Map<UUID, Boolean> canSimulate = new HashMap<>();
        access.resolveForProject(projectId).versions().forEach(v -> canSimulate.put(v.versionId(), v.canSimulate()));
        List<Version> versions = project.getVersions().stream()
                .filter(Version::isActive)
                .sorted(Comparator.comparing(BaseEntity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
        Map<UUID, List<Scenario>> byVersion = new HashMap<>();
        if (!versions.isEmpty()) {
            scenarioRepository.findByVersionIdInAndActiveTrueOrderByCreatedAtAscIdAsc(versions.stream().map(Version::getId).toList())
                    .forEach(s -> byVersion.computeIfAbsent(s.getVersion().getId(), id -> new ArrayList<>()).add(s));
        }
        List<ComparableScenarioDTO> result = new ArrayList<>();
        for (Version version : versions) {
            for (Scenario scenario : byVersion.getOrDefault(version.getId(), List.of())) {
                result.add(describe(project, version, scenario, scenarios.readinessOf(version, scenario),
                        canSimulate.getOrDefault(version.getId(), false)));
            }
        }
        return result;
    }

    /** The status of one scenario's latest simulation and when it was made. */
    public record Standing(ScenarioStatus status, java.time.Instant latestEvaluatedAt) {}

    /**
     * Where one scenario stands: simulating, never simulated, up to date or outdated. Outdated means its configuration, or
     * its version's nodes and connections, changed since the simulation: a node edit outdates every scenario of the version.
     */
    public Standing standingOf(Version version, Scenario scenario) {
        Optional<EconomicEvaluation> latest = evaluations.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().findFirst();
        java.time.Instant when = latest.map(BaseEntity::getCreatedAt).orElse(null);
        if (jobs.existsByScenarioIdAndStatusIn(scenario.getId(), List.of(EconomicJobStatus.QUEUED, EconomicJobStatus.RUNNING))) {
            return new Standing(ScenarioStatus.SIMULATING, when);
        }
        if (latest.isEmpty()) return new Standing(ScenarioStatus.NEVER_SIMULATED, null);
        EvaluationDTO evaluation = economics.evaluationOf(scenario, latest.get().getId());
        EconomicConfiguration current;
        try {
            current = economics.configuration(scenario);
        } catch (EntityNotFoundException missing) {
            return new Standing(ScenarioStatus.OUTDATED, when);
        }
        return new Standing(matches(version, current, evaluation.snapshot()) ? ScenarioStatus.UP_TO_DATE : ScenarioStatus.OUTDATED, when);
    }

    /** True when the snapshot was simulated on this scenario's current configuration and its version's physical model. */
    public boolean isUpToDate(Version version, Scenario scenario, EvaluationDTO.Snapshot snapshot) {
        if (snapshot == null || snapshot.configuration() == null || snapshot.physicalNodes() == null) return false;
        try {
            return matches(version, economics.configuration(scenario), snapshot);
        } catch (EntityNotFoundException invalid) {
            logger.debug("Scenario {} cannot be checked against its latest simulation: {}", scenario.getId(), invalid.getMessage());
            return false;
        }
    }

    /** True when the snapshot was simulated on the scenario's current configuration and physical model. */
    public boolean isUpToDate(Version version, EvaluationDTO.Snapshot snapshot) {
        if (snapshot == null || snapshot.configuration() == null || snapshot.physicalNodes() == null) return false;
        try {
            return matches(version, economics.configuration(version.getId()), snapshot);
        } catch (EntityNotFoundException invalid) {
            // No configuration: the old result cannot stand for the scenario.
            logger.debug("Scenario of version {} cannot be checked against its latest simulation: {}", version.getId(), invalid.getMessage());
            return false;
        }
    }

    private boolean matches(Version version, EconomicConfiguration current, EvaluationDTO.Snapshot snapshot) {
        if (snapshot == null || snapshot.configuration() == null || snapshot.physicalNodes() == null) return false;
        try {
            var input = codec.freeze(version, current, 1, 0);
            return canonical(snapshot.configuration()).equals(canonical(current))
                    && canonical(snapshot.physicalNodes()).equals(canonical(codec.displayNodes(input)))
                    && canonical(snapshot.physicalConnections()).equals(canonical(input.connections()));
        } catch (EntityNotFoundException | IllegalArgumentException invalid) {
            // No configuration, or one the simulator rejects: the old result cannot stand for the scenario.
            logger.debug("Scenario {} cannot be checked against its latest simulation: {}", version.getId(), invalid.getMessage());
            return false;
        }
    }

    private ComparableScenarioDTO describe(Project project, Version version, Scenario scenario, Readiness readiness, boolean canSimulate) {
        Optional<EconomicEvaluation> latest = evaluations.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().findFirst();
        Optional<EconomicJob> lastJob = jobs.findByScenarioIdOrderByCreatedAtDesc(scenario.getId()).stream().findFirst();
        boolean running = lastJob.map(job -> job.getStatus() == EconomicJobStatus.QUEUED
                || job.getStatus() == EconomicJobStatus.RUNNING).orElse(false);

        EvaluationDTO evaluation = latest.map(e -> economics.evaluationOf(scenario, e.getId())).orElse(null);
        ScenarioStatus status = running ? ScenarioStatus.SIMULATING
                : evaluation == null ? ScenarioStatus.NEVER_SIMULATED
                : isUpToDate(version, scenario, evaluation.snapshot()) ? ScenarioStatus.UP_TO_DATE
                : ScenarioStatus.OUTDATED;

        // Show the job while it runs, or when it failed after the result the comparison would use.
        SimulationJob job = lastJob.filter(j -> running || (j.getStatus() != EconomicJobStatus.COMPLETED
                        && (evaluation == null || j.getCreatedAt() == null || j.getCreatedAt().isAfter(evaluation.createdAt()))))
                .map(j -> new SimulationJob(j.getId(), j.getStatus().name(), j.getCompletedRounds(), j.getRounds(), j.getErrorMessage()))
                .orElse(null);

        EvaluationDTO.Snapshot snapshot = evaluation == null ? null : evaluation.snapshot();
        boolean probabilistic = snapshot != null && (snapshot.schemaVersion() >= 3 || snapshot.summary() != null);
        return new ComparableScenarioDTO(project.getId(), project.getName(), version.getId(), version.getName(),
                scenario.getId(), scenario.getName(), scenario.isDefaultScenario(), status,
                evaluation == null ? null : evaluation.id(), evaluation == null ? null : evaluation.createdAt(),
                snapshot == null ? null : probabilistic ? EvaluationMode.PROBABILISTIC : EvaluationMode.DETERMINISTIC,
                snapshot == null || snapshot.simulation() == null ? null : snapshot.simulation().rounds(),
                readiness.ready(), readiness.errors(), canSimulate, job);
    }

    /** JSON text round trip, then volatile keys dropped and arrays of entities sorted by id. */
    private JsonNode canonical(Object value) {
        try {
            JsonNode tree = mapper.readTree(mapper.writeValueAsString(value));
            return normalize(tree);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Cannot compare scenario inputs", error);
        }
    }

    private JsonNode normalize(JsonNode node) {
        if (node instanceof ObjectNode object) {
            ObjectNode copy = mapper.createObjectNode();
            object.fieldNames().forEachRemaining(name -> {
                if (!IGNORED_KEYS.contains(name)) copy.set(name, normalize(object.get(name)));
            });
            return copy;
        }
        if (node instanceof ArrayNode array) {
            List<JsonNode> items = new ArrayList<>();
            array.forEach(item -> items.add(normalize(item)));
            // Snapshot lists come from bags with no guaranteed order: compare entities as sets.
            if (items.stream().allMatch(item -> item.hasNonNull("id"))) {
                items.sort(Comparator.comparing(item -> item.get("id").asText()));
            }
            ArrayNode copy = mapper.createArrayNode();
            items.forEach(copy::add);
            return copy;
        }
        return node;
    }
}
