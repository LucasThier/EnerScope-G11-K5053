package org.enerscope.simulation.service;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.simulation.dto.NodeResultDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationKpisDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.dto.VerticalResultDTO;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.NodeCostResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.results.ResultPerRound;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Turns a stored {@link FinalResult} into the API's read models. Pure — no
 * repositories, no session — so the figures a comparison shows and the ones a
 * single run shows come from the same arithmetic.
 */
@Component
public class SimulationResultMapper {

    /** Some nodes sat on a cycle, could not be ordered, and produced nothing. */
    public static final String WARNING_UNSIMULATED_NODES = "UNSIMULATED_NODES";
    /** The model has no carrier or consumption node, so nothing counts as delivered. */
    public static final String WARNING_NO_SINKS = "NO_SINKS";

    private static final Set<String> EXPORT_CLASSES = Set.of(LNGCarrier.class.getSimpleName());
    private static final Set<String> DOMESTIC_CLASSES = Set.of(
            InternalConsumption.class.getSimpleName(), IndustrialConsumption.class.getSimpleName());

    public SimulationSummaryDTO toSummary(FinalResult result, UUID versionId) {
        double deliveredP50 = sumProduced(result.getPercentile50(), ResultPerRound.SINK_CLASSES);
        BigDecimal totalCost = result.totalCost();
        BigDecimal unitCost = deliveredP50 > 0
                ? totalCost.divide(BigDecimal.valueOf(deliveredP50), 4, RoundingMode.HALF_UP)
                : null;

        SimulationKpisDTO kpis = new SimulationKpisDTO(
                result.getMediaOutput(),
                sumProduced(result.getPercentile10(), ResultPerRound.SINK_CLASSES),
                deliveredP50,
                sumProduced(result.getPercentile90(), ResultPerRound.SINK_CLASSES),
                sumProduced(result.getPercentile50(), EXPORT_CLASSES),
                sumProduced(result.getPercentile50(), DOMESTIC_CLASSES),
                sumLosses(result.getPercentile50()),
                result.getCapex(),
                result.getOpex(),
                result.getUpkeep(),
                totalCost,
                unitCost);

        return new SimulationSummaryDTO(result.getId(), versionId, result.getCreatedAt(), result.getTime(),
                result.getRounds(), result.getNodeCosts().size(), kpis, warnings(result));
    }

    public SimulationDetailDTO toDetail(FinalResult result, UUID versionId) {
        Map<UUID, ResultPerNode> p10 = byNode(result.getPercentile10());
        Map<UUID, ResultPerNode> p50 = byNode(result.getPercentile50());
        Map<UUID, ResultPerNode> p90 = byNode(result.getPercentile90());

        List<NodeResultDTO> nodes = sortedByVertical(result.getNodeCosts()).stream().map(cost -> {
            ResultPerNode median = p50.get(cost.getNodeId());
            Double produced = median == null ? null : (double) median.getTotalProduced();
            Double capacity = median == null ? null : (double) median.getMaxPossibleProduced();
            Double utilization = capacity == null || capacity <= 0 ? null : produced / capacity;
            return new NodeResultDTO(cost.getNodeId(), cost.getIdentityId(), cost.getNodeName(),
                    cost.getNodeType(), cost.getVertical(), cost.getNodeState(),
                    produced(p10.get(cost.getNodeId())), produced, produced(p90.get(cost.getNodeId())),
                    capacity, utilization, median == null ? null : (double) median.getExtra(),
                    cost.getCapex(), cost.getOpex(), cost.getUpkeep(), cost.total());
        }).toList();

        return new SimulationDetailDTO(toSummary(result, versionId), verticals(result.getNodeCosts(), p50), nodes);
    }

    /**
     * Groups node costs (and, when a P50 round is given, its losses) by vertical,
     * in value-chain order. With {@code p50 == null} — a cost estimate, where
     * nothing was simulated — losses are left {@code null}.
     */
    public List<VerticalResultDTO> verticals(List<NodeCostResult> costs, Map<UUID, ResultPerNode> p50) {
        Map<String, List<NodeCostResult>> byVertical = new LinkedHashMap<>();
        for (NodeCostResult cost : sortedByVertical(costs)) {
            byVertical.computeIfAbsent(verticalOf(cost), key -> new ArrayList<>()).add(cost);
        }
        List<VerticalResultDTO> verticals = new ArrayList<>();
        byVertical.forEach((vertical, group) -> {
            Double losses = null;
            if (p50 != null) {
                double sum = 0;
                for (NodeCostResult cost : group) {
                    ResultPerNode row = p50.get(cost.getNodeId());
                    sum += row == null ? 0 : row.getExtra();
                }
                losses = sum;
            }
            verticals.add(new VerticalResultDTO(vertical, group.size(), losses,
                    sum(group, NodeCostResult::getCapex), sum(group, NodeCostResult::getOpex),
                    sum(group, NodeCostResult::getUpkeep), sum(group, NodeCostResult::total)));
        });
        return verticals;
    }

    public static Map<UUID, ResultPerNode> byNode(List<ResultPerNode> rows) {
        Map<UUID, ResultPerNode> map = new HashMap<>();
        for (ResultPerNode row : rows) {
            map.put(row.getNodeID(), row);
        }
        return map;
    }

    private static List<String> warnings(FinalResult result) {
        List<String> warnings = new ArrayList<>();
        Map<UUID, ResultPerNode> simulated = byNode(result.getPercentile50());
        boolean someUnsimulated = result.getNodeCosts().stream()
                .anyMatch(cost -> !simulated.containsKey(cost.getNodeId()));
        if (someUnsimulated) {
            warnings.add(WARNING_UNSIMULATED_NODES);
        }
        boolean hasSink = result.getPercentile50().stream()
                .anyMatch(row -> ResultPerRound.SINK_CLASSES.contains(row.getNodeClass()));
        if (!hasSink) {
            warnings.add(WARNING_NO_SINKS);
        }
        return warnings;
    }

    private static List<NodeCostResult> sortedByVertical(List<NodeCostResult> costs) {
        return costs.stream()
                .sorted(Comparator.comparingInt((NodeCostResult cost) -> verticalRank(verticalOf(cost)))
                        .thenComparing(cost -> cost.getNodeName() == null ? "" : cost.getNodeName(),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static String verticalOf(NodeCostResult cost) {
        return cost.getVertical() == null ? "UNKNOWN" : cost.getVertical();
    }

    private static int verticalRank(String vertical) {
        try {
            return VerticalEnum.valueOf(vertical).ordinal();
        } catch (IllegalArgumentException e) {
            return VerticalEnum.values().length;
        }
    }

    private static Double produced(ResultPerNode row) {
        return row == null ? null : (double) row.getTotalProduced();
    }

    private static double sumProduced(List<ResultPerNode> rows, Set<String> nodeClasses) {
        double total = 0;
        for (ResultPerNode row : rows) {
            if (nodeClasses.contains(row.getNodeClass())) {
                total += row.getTotalProduced();
            }
        }
        return total;
    }

    private static double sumLosses(List<ResultPerNode> rows) {
        double total = 0;
        for (ResultPerNode row : rows) {
            total += row.getExtra();
        }
        return total;
    }

    private static BigDecimal sum(List<NodeCostResult> costs, Function<NodeCostResult, BigDecimal> amount) {
        BigDecimal total = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        for (NodeCostResult cost : costs) {
            total = total.add(amount.apply(cost));
        }
        return total;
    }
}
