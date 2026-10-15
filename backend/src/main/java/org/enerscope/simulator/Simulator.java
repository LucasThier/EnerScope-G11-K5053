package org.enerscope.simulator;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.enerscope.node.model.*;
import org.enerscope.node.model.extraction.*;
import org.enerscope.node.model.export.*;
import org.enerscope.node.model.liquefaction.*;
import org.enerscope.node.model.transportation.*;
import org.enerscope.simulator.simNode.*;
import org.enerscope.simulator.results.*;
import org.enerscope.version.model.Version;

/** One isolated physical realization. Public duration is years; each step is one hour. */
public class Simulator {
    public static final int HOURS_PER_YEAR = 8760;
    public static final String ALGORITHM = "JAVA_RANDOM_V1";
    public static final int CALCULATION_VERSION = 1;
    private final List<SimBaseNode> executionOrder;
    private final Map<UUID, BaseNode> physicalNodes = new HashMap<>();
    private final List<AnnualNodeMetrics> annualMetrics = new ArrayList<>();
    private final Map<UUID, double[]> previousMetrics = new HashMap<>();
    private final Integer operatingStartYear;
    private boolean executed;
    private ResultPerRound result;

    public Simulator(Version version) {
        this(version, null, 0L);
    }

    public Simulator(Version version, int operatingStartYear) {
        this(version, operatingStartYear, 0L);
    }

    public Simulator(Version version, Integer operatingStartYear, long roundSeed) {
        this.operatingStartYear = operatingStartYear;
        Map<UUID, SimBaseNode> nodes = new HashMap<>();
        for (BaseNode physical : Objects.requireNonNull(version.getNodeSnapshot(), "Node snapshot required")) {
            if (physical.getId() == null || nodes.containsKey(physical.getId()))
                throw new IllegalArgumentException("Unique node identifiers required");
            SimBaseNode node = transform(physical);
            if (node instanceof SimWell well) well.configureRandom(seedForNode(roundSeed, physical.getId()));
            nodes.put(physical.getId(), node);
            physicalNodes.put(physical.getId(), physical);
        }
        Map<UUID, List<UUID>> successors = new HashMap<>();
        Map<UUID, Integer> degrees = new HashMap<>();
        nodes.keySet().forEach(id -> { successors.put(id, new ArrayList<>()); degrees.put(id, 0); });
        Set<String> edges = new HashSet<>();
        for (NodeConnection connection : Objects.requireNonNull(version.getConnectionSnapshot(), "Connections required")) {
            UUID from = connection.getFromNodeId(), to = connection.getToNodeId();
            if (!nodes.containsKey(from) || !nodes.containsKey(to) || !edges.add(from + ":" + to))
                throw new IllegalArgumentException("Invalid or duplicate connection");
            successors.get(from).add(to); degrees.put(to, degrees.get(to) + 1);
            nodes.get(to).addPreviousNode(nodes.get(from));
        }
        Comparator<UUID> priority = Comparator.<UUID>comparingDouble(id ->
                nodes.get(id) instanceof SimPipelineConnection p ? p.getOutputPriority() : 0)
                .thenComparing(UUID::toString);
        PriorityQueue<UUID> ready = new PriorityQueue<>(priority);
        degrees.forEach((id, degree) -> { if (degree == 0) ready.add(id); });
        executionOrder = new ArrayList<>();
        while (!ready.isEmpty()) {
            UUID id = ready.remove(); executionOrder.add(nodes.get(id));
            for (UUID next : successors.get(id)) if (degrees.merge(next, -1, Integer::sum) == 0) ready.add(next);
        }
        if (executionOrder.size() != nodes.size()) throw new IllegalArgumentException("Simulation graph contains a cycle");
    }

    public static long seedForRound(long seed, int round) {
        return mix(seed ^ (0x9e3779b97f4a7c15L * (round + 1L)));
    }

    public static long seedForNode(long seed, UUID id) {
        return mix(seed ^ id.getMostSignificantBits() ^ Long.rotateLeft(id.getLeastSignificantBits(), 17));
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    public void simulate(int years) {
        if (executed) throw new IllegalStateException("Create a fresh simulator for each round");
        if (years < 1 || years > 100) throw new IllegalArgumentException("Simulation years must be 1 to 100");
        executed = true;
        for (int hour = 0; hour < years * HOURS_PER_YEAR; hour++) {
            if (Thread.currentThread().isInterrupted()) throw new IllegalStateException("Simulation interrupted");
            for (SimBaseNode node : executionOrder) step(node, hour);
            if ((hour + 1) % HOURS_PER_YEAR == 0) captureYear((hour + 1) / HOURS_PER_YEAR);
        }
        result = new ResultPerRound();
        result.setYear(years);
        result.addAllResultPerNodes(executionOrder.stream().map(SimBaseNode::createResult).toList());
    }

    public List<AnnualNodeMetrics> getAnnualMetrics() {
        return List.copyOf(annualMetrics);
    }

    public ResultPerRound getResult() {
        return result;
    }

    public List<SimBaseNode> getExecutionOrder() {
        return List.copyOf(executionOrder);
    }

    private void step(SimBaseNode node, int time) {
        if (operatingStartYear != null) {
            BaseNode physical = physicalNodes.get(node.getId());
            LocalDate start = physical.getStartupDate() == null ? LocalDate.of(operatingStartYear, 1, 1)
                    : physical.getStartupDate().atZone(ZoneOffset.UTC).toLocalDate();
            long startHour = (long) (start.getYear() - operatingStartYear) * HOURS_PER_YEAR
                    + Math.min(start.getDayOfYear() - 1, 364) * 24L;
            long endHour = startHour + Math.round(physical.getLifespanInMonths() * HOURS_PER_YEAR / 12.0);
            node.setCalendarControlled(true);
            node.setOperatingAgeHours(Math.toIntExact(time - startHour));
            if (!physical.isActive() || time < startHour || time >= endHour) {
                node.setLastSimulatedTime(time); return;
            }
        }
        node.simulate(time);
    }

    private void captureYear(int period) {
        for (SimBaseNode node : executionOrder) {
            double[] current = { node.getMeasuredInput(), node.getMeasuredOutput(), node.getMeasuredExported(),
                    node.getMeasuredLosses(), node.getMeasuredOperatingHours(), node.getMeasuredEvents() };
            double[] previous = previousMetrics.getOrDefault(node.getId(), new double[6]);
            BigDecimal[] delta = new BigDecimal[6];
            for (int i = 0; i < current.length; i++) {
                if (!Double.isFinite(current[i]) || current[i] < previous[i])
                    throw new IllegalArgumentException("Invalid simulator quantity for node " + node.getId());
                delta[i] = BigDecimal.valueOf(current[i] - previous[i]);
            }
            annualMetrics.add(new AnnualNodeMetrics(node.getId(), period, delta[0], delta[1], delta[2], delta[3], delta[4], delta[5]));
            previousMetrics.put(node.getId(), current);
        }
    }

    private SimBaseNode transform(BaseNode node) {
        if (node instanceof Well n)
            return new SimWell(n);
        if (node instanceof GatheringNetwork n)
            return new SimGatheringNetwork(n);
        if (node instanceof TreatmentPlant n)
            return new SimTreatmentPlant(n);
        if (node instanceof Pipeline n)
            return new SimPipeline(n);
        if (node instanceof PipelineConnection n)
            return new SimPipelineConnection(n);
        if (node instanceof CompressingPlant n)
            return new SimCompressingPlant(n);
        if (node instanceof GroundBasedLiquefactionPlant n)
            return new SimGroundBasedLiquefactionPlant(n);
        if (node instanceof FLNGUnit n)
            return new SimFLNGUnit(n);
        if (node instanceof SeaportTerminal n)
            return new SimSeaportTerminal(n);
        if (node instanceof LNGCarrier n)
            return new SimLNGCarrier(n);
        if (node instanceof InternalConsumption n)
            return new SimInternalConsumption(n);
        if (node instanceof IndustrialConsumption n)
            return new SimIndustrialConsumption(n);
        throw new IllegalArgumentException("Unsupported simulation node " + node.getClass().getSimpleName());
    }
}
