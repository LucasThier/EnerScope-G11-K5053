package org.enerscope.version.dto;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.version.model.Version;

/**
 * Lightweight view of a version (a scenario): what the versions list shows and
 * what every endpoint that creates or changes a version answers with. It never
 * carries the node/connection snapshot — the diagram endpoint is for that — nor
 * the change log or the simulation results, which is what the raw entity used to
 * put in every response.
 *
 * <p>Counts leave soft-deleted ({@code REMOVED}) nodes out, and the connections
 * that touch them, matching what a simulation of the version takes into
 * account.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VersionSummaryDTO {

    private UUID id;
    private String name;
    private UUID parentVersionId;
    private Instant createdAt;
    private int nodeCount;
    private int connectionCount;
    private int simulationCount;
    /** When the most recent simulation ran; {@code null} if the version was never simulated. */
    private Instant lastSimulatedAt;

    public static VersionSummaryDTO from(Version version) {
        Set<UUID> activeNodes = new HashSet<>();
        for (BaseNode node : version.getNodeSnapshot()) {
            if (node != null && node.getState() != NodeStateEnum.REMOVED) {
                activeNodes.add(node.getId());
            }
        }
        int connections = (int) version.getConnectionSnapshot().stream()
                .filter(c -> c != null && activeNodes.contains(c.getFromNodeId()) && activeNodes.contains(c.getToNodeId()))
                .count();
        Instant lastSimulatedAt = version.getFinalResults().stream()
                .map(FinalResult::getCreatedAt)
                .filter(Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
        return new VersionSummaryDTO(version.getId(), version.getName(),
                version.getParentVersion() == null ? null : version.getParentVersion().getId(),
                version.getCreatedAt(), activeNodes.size(), connections, version.getFinalResults().size(),
                lastSimulatedAt);
    }
}
