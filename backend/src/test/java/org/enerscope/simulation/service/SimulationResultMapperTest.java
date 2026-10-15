package org.enerscope.simulation.service;

import org.enerscope.simulation.dto.NodeResultDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationKpisDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.dto.VerticalResultDTO;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.NodeCostResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SimulationResultMapperTest {

    private final SimulationResultMapper mapper = new SimulationResultMapper();

    private final UUID well = UUID.randomUUID();
    private final UUID pipeline = UUID.randomUUID();
    private final UUID carrier = UUID.randomUUID();
    private final UUID consumption = UUID.randomUUID();

    private static ResultPerNode row(UUID node, String nodeClass, float produced, float capacity, float extra) {
        ResultPerNode row = new ResultPerNode(node, nodeClass, produced, 0f, capacity);
        row.setExtra(extra);
        return row;
    }

    private static NodeCostResult cost(UUID node, String name, String type, String vertical, int capex) {
        return new NodeCostResult(node, UUID.randomUUID(), name, type, vertical, "RUNNING",
                new BigDecimal(capex).setScale(2), new BigDecimal("10.00"), new BigDecimal("5.00"));
    }

    /** A run whose P50 round exported 300 and fed 200 to the domestic market. */
    private FinalResult run() {
        FinalResult result = new FinalResult(20);
        result.setRounds(10);
        result.setMediaOutput(480f);
        result.setPercentile10(new ArrayList<>(List.of(row(carrier, "LNGCarrier", 200, 400, 0),
                row(consumption, "InternalConsumption", 150, 300, 0))));
        result.setPercentile50(new ArrayList<>(List.of(row(well, "Well", 1000, 1000, 0),
                row(pipeline, "Pipeline", 600, 1200, 40),
                row(carrier, "LNGCarrier", 300, 400, 0),
                row(consumption, "InternalConsumption", 200, 300, 0))));
        result.setPercentile90(new ArrayList<>(List.of(row(carrier, "LNGCarrier", 400, 400, 0),
                row(consumption, "InternalConsumption", 300, 300, 0))));
        result.setNodeCosts(new ArrayList<>(List.of(
                cost(carrier, "Buque", "LNG_CAMER", "EXPORT", 500),
                cost(pipeline, "Gasoducto", "PIPELINE", "TRANSPORTATION", 300),
                cost(consumption, "Consumo", "INTERNAL_CONSUMPTION", "INTERNAL_CONSUMPTION", 100),
                cost(well, "Pozo", "WELL", "EXTRACTION", 100))));
        result.setCapex(new BigDecimal("1000.00"));
        result.setOpex(new BigDecimal("40.00"));
        result.setUpkeep(new BigDecimal("20.00"));
        return result;
    }

    @Test
    void deliveredCountsExportsAndDomesticConsumptionOnly() {
        SimulationKpisDTO kpis = mapper.toSummary(run(), UUID.randomUUID()).kpis();

        assertEquals(500, kpis.deliveredP50(), 0.001);
        assertEquals(350, kpis.deliveredP10(), 0.001);
        assertEquals(700, kpis.deliveredP90(), 0.001);
        assertEquals(300, kpis.exportedP50(), 0.001);
        assertEquals(200, kpis.domesticP50(), 0.001);
        assertEquals(480, kpis.deliveredMean(), 0.001);
    }

    @Test
    void lossesAddUpEveryNodesLossesInTheMedianRound() {
        assertEquals(40, mapper.toSummary(run(), UUID.randomUUID()).kpis().lossesP50(), 0.001);
    }

    @Test
    void unitCostIsTheTotalCostOverTheMedianDeliveredVolume() {
        SimulationKpisDTO kpis = mapper.toSummary(run(), UUID.randomUUID()).kpis();

        assertEquals(new BigDecimal("1060.00"), kpis.totalCost());
        assertEquals(new BigDecimal("2.1200"), kpis.unitCost());
    }

    @Test
    void unitCostIsNullWhenNothingWasDelivered() {
        FinalResult result = run();
        result.setPercentile50(new ArrayList<>(List.of(row(well, "Well", 1000, 1000, 0))));

        SimulationSummaryDTO summary = mapper.toSummary(result, UUID.randomUUID());

        assertNull(summary.kpis().unitCost());
        assertTrue(summary.warnings().contains(SimulationResultMapper.WARNING_NO_SINKS));
    }

    @Test
    void aNodeWithACostButNoProductionIsReportedAsUnsimulated() {
        FinalResult result = run();
        UUID onACycle = UUID.randomUUID();
        result.getNodeCosts().add(cost(onACycle, "Lazo", "PIPELINE", "TRANSPORTATION", 0));

        SimulationDetailDTO detail = mapper.toDetail(result, UUID.randomUUID());

        assertEquals(List.of(SimulationResultMapper.WARNING_UNSIMULATED_NODES), detail.summary().warnings());
        NodeResultDTO loop = detail.nodes().stream().filter(n -> n.nodeId().equals(onACycle)).findFirst().orElseThrow();
        assertNull(loop.producedP50());
        assertNull(loop.utilizationP50());
    }

    @Test
    void nodesAreListedInValueChainOrderWithTheirUtilization() {
        SimulationDetailDTO detail = mapper.toDetail(run(), UUID.randomUUID());

        assertEquals(List.of("Pozo", "Gasoducto", "Buque", "Consumo"),
                detail.nodes().stream().map(NodeResultDTO::name).toList());
        NodeResultDTO pipe = detail.nodes().get(1);
        assertEquals(600, pipe.producedP50(), 0.001);
        assertEquals(0.5, pipe.utilizationP50(), 0.0001);
        assertEquals(40, pipe.lossesP50(), 0.001);
        assertNull(pipe.producedP10(), "the pipeline has no row in the P10 round of this fixture");
        assertEquals(new BigDecimal("315.00"), pipe.totalCost());
        assertEquals(4, detail.summary().nodeCount());
    }

    @Test
    void verticalsGroupCostsAndLossesInValueChainOrder() {
        List<VerticalResultDTO> verticals = mapper.toDetail(run(), UUID.randomUUID()).verticals();

        assertEquals(List.of("EXTRACTION", "TRANSPORTATION", "EXPORT", "INTERNAL_CONSUMPTION"),
                verticals.stream().map(VerticalResultDTO::vertical).toList());
        VerticalResultDTO transport = verticals.get(1);
        assertEquals(1, transport.nodeCount());
        assertEquals(40, transport.lossesP50(), 0.001);
        assertEquals(new BigDecimal("300.00"), transport.capex());
        assertEquals(new BigDecimal("315.00"), transport.totalCost());
    }
}
