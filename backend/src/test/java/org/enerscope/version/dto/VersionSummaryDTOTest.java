package org.enerscope.version.dto;

import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.enerscope.simulation.SimulationFixtures.domesticChain;
import static org.junit.jupiter.api.Assertions.*;

class VersionSummaryDTOTest {

    @Test
    void countsTheActiveModelAndItsRuns() {
        Version parent = domesticChain("Base");
        Version version = domesticChain("Rama");
        ReflectionTestUtils.setField(version, "parentVersion", parent);
        FinalResult older = new FinalResult(10);
        older.setCreatedAt(Instant.parse("2026-10-01T00:00:00Z"));
        FinalResult newer = new FinalResult(10);
        newer.setCreatedAt(Instant.parse("2026-10-02T00:00:00Z"));
        version.getFinalResults().add(older);
        version.getFinalResults().add(newer);

        VersionSummaryDTO summary = VersionSummaryDTO.from(version);

        assertEquals(version.getId(), summary.getId());
        assertEquals("Rama", summary.getName());
        assertEquals(parent.getId(), summary.getParentVersionId());
        assertEquals(3, summary.getNodeCount());
        assertEquals(2, summary.getConnectionCount());
        assertEquals(2, summary.getSimulationCount());
        assertEquals(newer.getCreatedAt(), summary.getLastSimulatedAt());
    }

    @Test
    void aRemovedNodeAndItsConnectionsAreNotCounted() {
        Version version = domesticChain("Base");
        version.getNodeSnapshot().get(2).setState(NodeStateEnum.REMOVED);

        VersionSummaryDTO summary = VersionSummaryDTO.from(version);

        assertEquals(2, summary.getNodeCount());
        assertEquals(1, summary.getConnectionCount());
        assertNull(summary.getLastSimulatedAt());
        assertNull(summary.getParentVersionId());
    }

    @Test
    void aVersionWithNothingInItIsEmpty() {
        Version empty = new Version("Vacía", null, null, null, null, null);
        ReflectionTestUtils.setField(empty, "id", UUID.randomUUID());

        VersionSummaryDTO summary = VersionSummaryDTO.from(empty);

        assertEquals(0, summary.getNodeCount());
        assertEquals(0, summary.getConnectionCount());
        assertEquals(0, summary.getSimulationCount());
    }
}
