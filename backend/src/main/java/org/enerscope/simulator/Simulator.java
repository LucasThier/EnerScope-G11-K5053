package org.enerscope.simulator;

import lombok.Getter;
import lombok.Setter;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.export.IndustrialConsumption;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.export.LNGCarrier;
import org.enerscope.node.model.export.SeaportTerminal;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.TreatmentPlant;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.liquefaction.FLNGUnit;
import org.enerscope.node.model.liquefaction.GroundBasedLiquefactionPlant;
import org.enerscope.node.model.transportation.CompressingPlant;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.node.model.transportation.PipelineConnection;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.ResultPerNode;
import org.enerscope.simulator.results.ResultPerRound;
import org.enerscope.simulator.simNode.*;
import org.enerscope.version.model.Version;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@Getter
@Setter
public class Simulator {

    /** Monte Carlo rounds run by {@link #simulate(int)}. */
    public static final int DEFAULT_ROUNDS = 10;

    private UUID versionID;

    /**
     * Execution order of a graph built from the version, kept for inspection.
     * Each round of {@link #simulate(int, int)} runs on a fresh graph of its own,
     * so this one is never mutated by a simulation.
     */
    private List<SimBaseNode> executionOrder;

    /**
     * Nodes of the version that are not in {@link #executionOrder}: they sit on a
     * cycle (or downstream of one), so a topological order cannot place them and
     * they never produce. {@code REMOVED} nodes are not listed — they are left out
     * on purpose, not because of the graph's shape.
     */
    private List<UUID> nodesOutsideExecutionOrder;

    private Version version;
    private List<ResultPerRound> resultsPerRound;
    private FinalResult finalResult;

    public Simulator(Version version){
        this.version = version;
        this.resultsPerRound = new ArrayList<>();
        this.executionOrder = buildExecutionOrder();
        Set<UUID> ordered = new HashSet<>();
        executionOrder.forEach(node -> ordered.add(node.getId()));
        this.nodesOutsideExecutionOrder = simulatedNodes().stream()
                .map(BaseNode::getId)
                .filter(id -> !ordered.contains(id))
                .toList();
    }

    /**
     * The nodes that take part in a simulation: every node in the version's
     * snapshot except the soft-deleted ones. The editor deletes a node by setting
     * it {@code REMOVED}, and a deleted node must not keep producing.
     */
    private List<BaseNode> simulatedNodes() {
        List<BaseNode> nodes = new ArrayList<>();
        for (BaseNode baseNode : version.getNodeSnapshot()) {
            if (baseNode != null && baseNode.getState() != NodeStateEnum.REMOVED) {
                nodes.add(baseNode);
            }
        }
        return nodes;
    }

    /**
     * Builds an independent graph of simulation nodes for the version and
     * returns it in execution order. Every call creates new nodes, so separate
     * rounds never share state.
     */
    private List<SimBaseNode> buildExecutionOrder() {
        Map<UUID, SimBaseNode> simNodesById = new HashMap<>();
        List<SimBaseNode> allNodes = new ArrayList<>();

        simulatedNodes().forEach(baseNode -> {
            SimBaseNode simNode = transformNode(baseNode);
            if (simNode != null) {
                simNodesById.put(baseNode.getId(), simNode);
                allNodes.add(simNode);
            }
        });

        Map<SimBaseNode, List<SimBaseNode>> successors = new HashMap<>();
        Map<SimBaseNode, Integer> inDegree = new HashMap<>();

        allNodes.forEach(node -> {
            successors.put(node, new ArrayList<>());
            inDegree.put(node, 0);
        });

        version.getConnectionSnapshot().forEach(connection -> {
            SimBaseNode fromNode = simNodesById.get(connection.getFromNodeId());
            SimBaseNode toNode = simNodesById.get(connection.getToNodeId());

            if (fromNode != null && toNode != null) {
                successors.get(fromNode).add(toNode);
                inDegree.put(toNode, inDegree.get(toNode) + 1);
                toNode.addPreviousNode(fromNode);
            }
        });

        return computeTopologicalOrderWithPriority(allNodes, successors, inDegree);
    }

    private List<SimBaseNode> computeTopologicalOrderWithPriority(List<SimBaseNode> allNodes, Map<SimBaseNode, List<SimBaseNode>> successors, Map<SimBaseNode, Integer> inDegree) {

        Queue<SimBaseNode> queue = new LinkedList<>();

        allNodes.stream().filter(node -> inDegree.get(node) == 0).forEach(queue::add);

        List<SimBaseNode> sortedList = new ArrayList<>();

        while (!queue.isEmpty()) {
            SimBaseNode current = queue.poll();
            sortedList.add(current);

            List<SimBaseNode> nextNodes = new ArrayList<>(successors.get(current));

            nextNodes.sort((n1, n2) -> {
                if (n1 instanceof SimPipelineConnection p1 && n2 instanceof SimPipelineConnection p2) {
                    return Float.compare(p1.getOutputPriority(), p2.getOutputPriority());
                }
                return 0;
            });

            for (SimBaseNode neighbor : nextNodes) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.add(neighbor);
                }
            }
        }

        return sortedList;
    }

    public void simulate(int time){
        simulate(time, DEFAULT_ROUNDS);
    }

    /**
     * Runs {@code rounds} Monte Carlo rounds of {@code years} years each, hour by
     * hour, and aggregates them into {@link #finalResult}.
     *
     * <p>Rounds are independent by construction — each one gets a graph of its
     * own from {@link #buildExecutionOrder()} — so they run in parallel, one per
     * available core. Before this change they ran in sequence on one shared graph
     * reset between rounds, and {@code reset()} misses part of the state (the
     * pipelines' in-transit batches, the maintenance counters), which leaked from
     * one round into the next.</p>
     */
    public void simulate(int years, int rounds) {
        if (years < 1) {
            throw new IllegalArgumentException("The simulation horizon must be at least one year");
        }
        if (rounds < 1) {
            throw new IllegalArgumentException("A simulation needs at least one round");
        }
        int timeInHours = years * 24 * 365;

        // Graphs are built here, on the calling thread: only the rounds themselves
        // run on the pool, so the version's entities are never read concurrently.
        List<List<SimBaseNode>> graphs = new ArrayList<>(rounds);
        for (int round = 0; round < rounds; round++) {
            graphs.add(buildExecutionOrder());
        }

        int threads = Math.max(1, Math.min(rounds, Runtime.getRuntime().availableProcessors()));
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<ResultPerRound>> futures = new ArrayList<>(rounds);
            for (List<SimBaseNode> graph : graphs) {
                futures.add(pool.submit(() -> runRound(graph, timeInHours)));
            }
            List<ResultPerRound> results = new ArrayList<>(rounds);
            for (Future<ResultPerRound> future : futures) {
                results.add(future.get());
            }
            this.resultsPerRound = results;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The simulation was interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("The simulation failed", cause);
        } finally {
            pool.shutdownNow();
        }

        orderResults();
        createFinalResult(years, rounds);

        version.addResult(finalResult);
    }

    private static ResultPerRound runRound(List<SimBaseNode> graph, int timeInHours) {
        for (int count = 0; count <= timeInHours; count++) {
            for (SimBaseNode node : graph) {
                node.simulate(count);
            }
        }
        ResultPerRound resultPerRound = new ResultPerRound();
        resultPerRound.addAllResultPerNodes(graph.stream().map(SimBaseNode::createResult).toList());
        return resultPerRound;
    }

    private void orderResults() {
        List<ResultPerRound> resultsPerRoundOrder = this.resultsPerRound.stream().sorted(Comparator.comparingDouble(ResultPerRound::amountDelivered)).toList();
        this.resultsPerRound = resultsPerRoundOrder;
    }

    private void createFinalResult(int time, int rounds) {
        FinalResult finalResult = new FinalResult(time);
        finalResult.setRounds(rounds);
        finalResult.setMediaOutput(mediaOutput());
        // Copies, not the rounds' own rows: with few rounds two percentiles can
        // land on the same round, and one row cannot belong to two collections.
        finalResult.setPercentile90(copyOf(getPercentile(90).getResultPerNodes()));
        finalResult.setPercentile50(copyOf(getPercentile(50).getResultPerNodes()));
        finalResult.setPercentile10(copyOf(getPercentile(10).getResultPerNodes()));
        this.finalResult = finalResult;
    }

    private static List<ResultPerNode> copyOf(List<ResultPerNode> results) {
        List<ResultPerNode> copies = new ArrayList<>(results.size());
        for (ResultPerNode result : results) {
            ResultPerNode copy = new ResultPerNode(result.getNodeID(), result.getNodeClass(),
                    result.getTotalProduced(), result.getTotalDeferred(), result.getMaxPossibleProduced());
            copy.setExtra(result.getExtra());
            copies.add(copy);
        }
        return copies;
    }

    private float mediaOutput(){
        int size = this.resultsPerRound.size();
        float sum = (float) this.resultsPerRound.stream().mapToDouble(ResultPerRound::amountDelivered).sum();
        return sum / size;
    }

    private ResultPerRound getPercentile(double percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * resultsPerRound.size()) - 1;
        if (index < 0) index = 0;
        if (index >= resultsPerRound.size()) index = resultsPerRound.size() - 1;
        return resultsPerRound.get(index);
    }

    private SimBaseNode transformNode(BaseNode baseNode) {
        SimBaseNode simNode = null;

        if (baseNode instanceof Well well) {
            SimWell simWell = new SimWell(well);
            simNode = simWell;
        } else if (baseNode instanceof GatheringNetwork gatheringNetwork) {
            SimGatheringNetwork simGatheringNetwork = new SimGatheringNetwork(gatheringNetwork);
            simNode = simGatheringNetwork;
        } else if (baseNode instanceof TreatmentPlant treatmentPlants) {
            SimTreatmentPlant simTreatmentPlant = new SimTreatmentPlant(treatmentPlants);
            simNode = simTreatmentPlant;
        } else if (baseNode instanceof Pipeline pipeline) {
            SimPipeline simPipeline = new SimPipeline(pipeline);
            simNode = simPipeline;
        } else if (baseNode instanceof PipelineConnection pipelineConnection) {
            SimPipelineConnection simPipelineConnection = new SimPipelineConnection(pipelineConnection);
            simNode = simPipelineConnection;
        } else if (baseNode instanceof CompressingPlant compressingPlant) {
            SimCompressingPlant simCompressingPlant = new SimCompressingPlant(compressingPlant);
            simNode = simCompressingPlant;
        } else if (baseNode instanceof GroundBasedLiquefactionPlant groundBasedLiquefactionPlant) {
            SimGroundBasedLiquefactionPlant simGroundBasedLiquefactionPlant = new SimGroundBasedLiquefactionPlant(groundBasedLiquefactionPlant);
            simNode = simGroundBasedLiquefactionPlant;
        } else if (baseNode instanceof FLNGUnit flngUnit) {
            SimFLNGUnit simFLNGUnit = new SimFLNGUnit(flngUnit);
            simNode = simFLNGUnit;
        } else if (baseNode instanceof SeaportTerminal seaportTerminal) {
            SimSeaportTerminal simSeaportTerminal = new SimSeaportTerminal(seaportTerminal);
            simNode = simSeaportTerminal;
        } else if (baseNode instanceof LNGCarrier lngCarrier) {
            SimLNGCarrier simLNGCarrier = new SimLNGCarrier(lngCarrier);
            simNode = simLNGCarrier;
        } else if (baseNode instanceof InternalConsumption internalConsumption) {
            SimInternalConsumption simInternalConsumption = new SimInternalConsumption(internalConsumption);
            simNode = simInternalConsumption;
        } else if (baseNode instanceof IndustrialConsumption industrialConsumption) {
            SimIndustrialConsumption simIndustrialConsumption = new SimIndustrialConsumption(industrialConsumption);
            simNode = simIndustrialConsumption;
        }

        return simNode;
    }

}
