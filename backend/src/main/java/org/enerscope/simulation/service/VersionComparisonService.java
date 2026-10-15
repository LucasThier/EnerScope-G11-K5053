package org.enerscope.simulation.service;

import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.simulation.dto.ComparedVersionDTO;
import org.enerscope.simulation.dto.NodeDiffDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.dto.StructuralDiffDTO;
import org.enerscope.simulation.dto.VersionComparisonDTO;
import org.enerscope.simulation.dto.VerticalResultDTO;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Puts two or more versions of one project side by side: what each model looks
 * like, how it differs from the first one (the baseline), and the figures of its
 * latest simulation run.
 *
 * <p>Every version must belong to the project in the path. Without that check
 * the endpoint would read any version the caller can name, by passing a project
 * they do belong to.</p>
 */
@Service
public class VersionComparisonService {

    public static final int MAX_VERSIONS = 6;
    public static final String WARNING_MISSING_SIMULATIONS = "MISSING_SIMULATIONS";
    public static final String WARNING_DIFFERENT_HORIZONS = "DIFFERENT_HORIZONS";

    private final ProjectRepository projectRepository;
    private final VersionRepository versionRepository;
    private final ProjectAccessGuard accessGuard;
    private final SimulationResultMapper mapper;
    private final AppLogger logger;

    public VersionComparisonService(ProjectRepository projectRepository,
                                    VersionRepository versionRepository,
                                    ProjectAccessGuard accessGuard,
                                    SimulationResultMapper mapper,
                                    AppLogger logger) {
        this.projectRepository = projectRepository;
        this.versionRepository = versionRepository;
        this.accessGuard = accessGuard;
        this.mapper = mapper;
        this.logger = logger;
    }

    @Transactional(readOnly = true)
    public VersionComparisonDTO compare(UUID projectId, List<UUID> versionIds) {
        Objects.requireNonNull(projectId, "Project ID cannot be null");
        List<UUID> ids = versionIds == null ? List.of()
                : new ArrayList<>(new LinkedHashSet<>(versionIds.stream().filter(Objects::nonNull).toList()));
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Pick at least two different versions to compare");
        }
        if (ids.size() > MAX_VERSIONS) {
            throw new IllegalArgumentException("At most " + MAX_VERSIONS + " versions can be compared at once");
        }
        if (!projectRepository.existsByIdAndActiveTrue(projectId)) {
            throw new IllegalArgumentException("Project not found");
        }
        accessGuard.assertCanViewProject(projectId);

        List<Version> versions = new ArrayList<>();
        for (UUID id : ids) {
            Version version = versionRepository.findById(id).orElseThrow(() -> new VersionNotFoundException(id));
            UUID owner = projectRepository.findIdByVersionId(id).orElse(null);
            if (!projectId.equals(owner)) {
                logger.warn("Version {} was compared under project {}, which does not own it", id, projectId);
                throw new IllegalArgumentException("Version " + id + " does not belong to this project");
            }
            versions.add(version);
        }

        Version baseline = versions.get(0);
        List<ComparedVersionDTO> compared = new ArrayList<>();
        for (Version version : versions) {
            compared.add(compareOne(version, version == baseline ? null : baseline));
        }
        return new VersionComparisonDTO(projectId, baseline.getId(), compared, warnings(compared));
    }

    private ComparedVersionDTO compareOne(Version version, Version baseline) {
        List<BaseNode> nodes = SimulationService.simulatedNodes(version);
        Map<String, Integer> byVertical = new LinkedHashMap<>();
        nodes.stream()
                .map(node -> node.getType() == null || node.getType().getVertical() == null
                        ? "UNKNOWN" : node.getType().getVertical().name())
                .sorted()
                .forEach(vertical -> byVertical.merge(vertical, 1, Integer::sum));

        FinalResult latest = latestRun(version);
        SimulationSummaryDTO summary = latest == null ? null : mapper.toSummary(latest, version.getId());
        List<VerticalResultDTO> verticals = latest == null ? null
                : mapper.verticals(latest.getNodeCosts(), SimulationResultMapper.byNode(latest.getPercentile50()));

        return new ComparedVersionDTO(version.getId(), version.getName(),
                version.getParentVersion() == null ? null : version.getParentVersion().getId(),
                version.getCreatedAt(), nodes.size(), activeConnections(version, nodes).size(), byVertical,
                baseline == null ? null : diff(baseline, version), summary, verticals);
    }

    /**
     * Diffs {@code version} against {@code baseline} by node identity. A node
     * soft-deleted in one of them counts as absent there, matching what the
     * engine simulates.
     */
    StructuralDiffDTO diff(Version baseline, Version version) {
        Map<UUID, BaseNode> before = byIdentity(SimulationService.simulatedNodes(baseline));
        Map<UUID, BaseNode> after = byIdentity(SimulationService.simulatedNodes(version));

        List<NodeDiffDTO> changes = new ArrayList<>();
        int added = 0;
        int removed = 0;
        int modified = 0;
        int unchanged = 0;
        for (Map.Entry<UUID, BaseNode> entry : after.entrySet()) {
            BaseNode current = entry.getValue();
            BaseNode previous = before.get(entry.getKey());
            if (previous == null) {
                added++;
                changes.add(change(current, "ADDED", List.of()));
                continue;
            }
            List<String> fields = NodeFingerprint.differences(NodeFingerprint.of(previous), NodeFingerprint.of(current));
            if (fields.isEmpty()) {
                unchanged++;
            } else {
                modified++;
                changes.add(change(current, "MODIFIED", fields));
            }
        }
        for (Map.Entry<UUID, BaseNode> entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                removed++;
                changes.add(change(entry.getValue(), "REMOVED", List.of()));
            }
        }
        changes.sort(Comparator.comparing(NodeDiffDTO::change).thenComparing(
                diff -> diff.name() == null ? "" : diff.name(), String.CASE_INSENSITIVE_ORDER));

        Set<List<UUID>> linksBefore = links(baseline);
        Set<List<UUID>> linksAfter = links(version);
        int connectionsAdded = (int) linksAfter.stream().filter(link -> !linksBefore.contains(link)).count();
        int connectionsRemoved = (int) linksBefore.stream().filter(link -> !linksAfter.contains(link)).count();

        return new StructuralDiffDTO(added, removed, modified, unchanged, connectionsAdded, connectionsRemoved, changes);
    }

    private static FinalResult latestRun(Version version) {
        return version.getFinalResults().stream()
                .max(Comparator.comparing(FinalResult::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private static List<String> warnings(List<ComparedVersionDTO> compared) {
        List<String> warnings = new ArrayList<>();
        if (compared.stream().anyMatch(version -> version.latestSimulation() == null)) {
            warnings.add(WARNING_MISSING_SIMULATIONS);
        }
        long horizons = compared.stream().map(ComparedVersionDTO::latestSimulation).filter(Objects::nonNull)
                .map(SimulationSummaryDTO::years).distinct().count();
        if (horizons > 1) {
            warnings.add(WARNING_DIFFERENT_HORIZONS);
        }
        return warnings;
    }

    private static NodeDiffDTO change(BaseNode node, String change, List<String> fields) {
        NodeTypeData type = node.getType();
        return new NodeDiffDTO(node.getIdentityId(), node.getName(),
                type == null || type.getNodeType() == null ? null : type.getNodeType().name(), change, fields);
    }

    private static Map<UUID, BaseNode> byIdentity(List<BaseNode> nodes) {
        Map<UUID, BaseNode> map = new LinkedHashMap<>();
        for (BaseNode node : nodes) {
            map.put(node.getIdentityId() != null ? node.getIdentityId() : node.getId(), node);
        }
        return map;
    }

    /** Connections between two active nodes of the version. */
    private static List<NodeConnection> activeConnections(Version version, List<BaseNode> activeNodes) {
        Set<UUID> ids = new HashSet<>();
        activeNodes.forEach(node -> ids.add(node.getId()));
        return version.getConnectionSnapshot().stream()
                .filter(connection -> ids.contains(connection.getFromNodeId()) && ids.contains(connection.getToNodeId()))
                .toList();
    }

    /** The version's connections as (from identity, to identity) pairs. */
    private static Set<List<UUID>> links(Version version) {
        List<BaseNode> nodes = SimulationService.simulatedNodes(version);
        Map<UUID, UUID> identityById = new LinkedHashMap<>();
        nodes.forEach(node -> identityById.put(node.getId(),
                node.getIdentityId() != null ? node.getIdentityId() : node.getId()));
        Set<List<UUID>> links = new LinkedHashSet<>();
        for (NodeConnection connection : activeConnections(version, nodes)) {
            links.add(List.of(identityById.get(connection.getFromNodeId()),
                    identityById.get(connection.getToNodeId())));
        }
        return links;
    }
}
