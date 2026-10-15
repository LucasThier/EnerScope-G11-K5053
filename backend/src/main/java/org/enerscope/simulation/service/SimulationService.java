package org.enerscope.simulation.service;

import org.enerscope.common.EntityNotFoundException;
import org.enerscope.common.VersionNotFoundException;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.project.service.ProjectAccessGuard;
import org.enerscope.simulation.dto.CostEstimateDTO;
import org.enerscope.simulation.dto.RunSimulationRequestDTO;
import org.enerscope.simulation.dto.SimulationDetailDTO;
import org.enerscope.simulation.dto.SimulationSummaryDTO;
import org.enerscope.simulation.dto.VerticalResultDTO;
import org.enerscope.simulation.repository.FinalResultRepository;
import org.enerscope.simulator.Simulator;
import org.enerscope.simulator.results.FinalResult;
import org.enerscope.simulator.results.NodeCostResult;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Runs the simulation engine on a version and keeps the result.
 *
 * <p>A run is the engine's production figures plus the cost of the simulated
 * model over the same horizon, both stored with the version
 * ({@link Version#getFinalResults()}). Costs are snapshotted rather than
 * recomputed on read so a run keeps describing the model that produced it after
 * the version is edited; {@link #estimateCosts} is the live counterpart.</p>
 *
 * <p>Authorization follows the rest of {@code /version/**}: running or deleting a
 * run changes the version, so it needs {@code EDIT_PROJECT}; reading needs
 * membership. Every check runs after the version is resolved, so an unknown id
 * answers 404 rather than 403.</p>
 */
@Service
public class SimulationService {

    private final VersionRepository versionRepository;
    private final FinalResultRepository finalResultRepository;
    private final ProjectAccessGuard accessGuard;
    private final CostCalculator costCalculator;
    private final SimulationResultMapper mapper;
    private final AppLogger logger;

    public SimulationService(VersionRepository versionRepository,
                             FinalResultRepository finalResultRepository,
                             ProjectAccessGuard accessGuard,
                             CostCalculator costCalculator,
                             SimulationResultMapper mapper,
                             AppLogger logger) {
        this.versionRepository = versionRepository;
        this.finalResultRepository = finalResultRepository;
        this.accessGuard = accessGuard;
        this.costCalculator = costCalculator;
        this.mapper = mapper;
        this.logger = logger;
    }

    /**
     * Simulates the version and stores the run. The engine runs inside the
     * transaction — it needs the version's lazy snapshot loaded — so the request
     * holds a connection for as long as the simulation takes; the caps on
     * {@link RunSimulationRequestDTO} keep that bounded.
     */
    @Transactional
    public SimulationDetailDTO runSimulation(UUID versionId, RunSimulationRequestDTO request) {
        Objects.requireNonNull(request, "Simulation request cannot be null");
        int years = request.years() == null ? 0 : request.years();
        int rounds = request.rounds() == null ? Simulator.DEFAULT_ROUNDS : request.rounds();
        if (years < 1 || years > RunSimulationRequestDTO.MAX_YEARS) {
            throw new IllegalArgumentException(
                    "The simulation horizon must be between 1 and " + RunSimulationRequestDTO.MAX_YEARS + " years");
        }
        if (rounds < 1 || rounds > RunSimulationRequestDTO.MAX_ROUNDS) {
            throw new IllegalArgumentException(
                    "The number of rounds must be between 1 and " + RunSimulationRequestDTO.MAX_ROUNDS);
        }

        Version version = findVersion(versionId);
        accessGuard.assertCanEditVersion(versionId);

        List<BaseNode> nodes = simulatedNodes(version);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("The version has no nodes to simulate");
        }

        long start = System.currentTimeMillis();
        Simulator simulator = new Simulator(version);
        simulator.simulate(years, rounds);
        FinalResult result = simulator.getFinalResult();
        applyCosts(result, nodes, years);

        if (!simulator.getNodesOutsideExecutionOrder().isEmpty()) {
            logger.warn("Version {} has {} node(s) on a cycle; they were not simulated",
                    versionId, simulator.getNodesOutsideExecutionOrder().size());
        }

        // The engine already added the run to the version's collection; saving the
        // run itself keeps this instance the managed one (see FinalResultRepository),
        // and the flush writes the version link along with it.
        finalResultRepository.saveAndFlush(result);
        logger.info("Simulated version {} ({} nodes) over {} years with {} rounds in {} ms",
                version.getName(), nodes.size(), years, rounds, System.currentTimeMillis() - start);
        return mapper.toDetail(result, versionId);
    }

    /** The version's runs, newest first. */
    @Transactional(readOnly = true)
    public List<SimulationSummaryDTO> listSimulations(UUID versionId) {
        Version version = findVersion(versionId);
        accessGuard.assertCanViewVersion(versionId);
        return newestFirst(version.getFinalResults()).stream()
                .map(result -> mapper.toSummary(result, versionId))
                .toList();
    }

    @Transactional(readOnly = true)
    public SimulationDetailDTO getSimulation(UUID versionId, UUID simulationId) {
        Version version = findVersion(versionId);
        accessGuard.assertCanViewVersion(versionId);
        return mapper.toDetail(findRun(version, simulationId), versionId);
    }

    @Transactional
    public void deleteSimulation(UUID versionId, UUID simulationId) {
        Version version = findVersion(versionId);
        accessGuard.assertCanEditVersion(versionId);
        FinalResult run = findRun(version, simulationId);
        version.getFinalResults().remove(run);
        versionRepository.save(version);
        logger.info("Deleted simulation {} of version {}", simulationId, version.getName());
    }

    /** Cost of the version's current model over {@code years}, without simulating it. */
    @Transactional(readOnly = true)
    public CostEstimateDTO estimateCosts(UUID versionId, int years) {
        if (years < 1 || years > RunSimulationRequestDTO.MAX_YEARS) {
            throw new IllegalArgumentException(
                    "The cost horizon must be between 1 and " + RunSimulationRequestDTO.MAX_YEARS + " years");
        }
        Version version = findVersion(versionId);
        accessGuard.assertCanViewVersion(versionId);

        List<NodeCostResult> costs = toCostRows(simulatedNodes(version), years);
        List<VerticalResultDTO> verticals = mapper.verticals(costs, null);
        BigDecimal capex = sum(verticals, VerticalResultDTO::capex);
        BigDecimal opex = sum(verticals, VerticalResultDTO::opex);
        BigDecimal upkeep = sum(verticals, VerticalResultDTO::upkeep);
        return new CostEstimateDTO(years, capex, opex, upkeep, capex.add(opex).add(upkeep), verticals);
    }

    /** The nodes the engine and the cost calculation both take into account: all but the soft-deleted. */
    static List<BaseNode> simulatedNodes(Version version) {
        return version.getNodeSnapshot().stream()
                .filter(node -> node != null && node.getState() != NodeStateEnum.REMOVED)
                .toList();
    }

    private void applyCosts(FinalResult result, List<BaseNode> nodes, int years) {
        List<NodeCostResult> costs = toCostRows(nodes, years);
        result.getNodeCosts().addAll(costs);
        result.setCapex(costs.stream().map(NodeCostResult::getCapex).reduce(zero(), BigDecimal::add));
        result.setOpex(costs.stream().map(NodeCostResult::getOpex).reduce(zero(), BigDecimal::add));
        result.setUpkeep(costs.stream().map(NodeCostResult::getUpkeep).reduce(zero(), BigDecimal::add));
    }

    private List<NodeCostResult> toCostRows(List<BaseNode> nodes, int years) {
        return costCalculator.costOf(nodes, years).stream().map(cost -> {
            BaseNode node = cost.node();
            NodeTypeData type = node.getType();
            return new NodeCostResult(node.getId(), node.getIdentityId(), node.getName(),
                    type == null || type.getNodeType() == null ? null : type.getNodeType().name(),
                    type == null || type.getVertical() == null ? null : type.getVertical().name(),
                    node.getState() == null ? null : node.getState().name(),
                    cost.capex(), cost.opex(), cost.upkeep());
        }).toList();
    }

    private Version findVersion(UUID versionId) {
        Objects.requireNonNull(versionId, "Version ID cannot be null");
        return versionRepository.findById(versionId)
                .orElseThrow(() -> new VersionNotFoundException(versionId));
    }

    private static FinalResult findRun(Version version, UUID simulationId) {
        return version.getFinalResults().stream()
                .filter(run -> run.getId() != null && run.getId().equals(simulationId))
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("Simulation not found with id: " + simulationId));
    }

    private static List<FinalResult> newestFirst(List<FinalResult> runs) {
        return runs.stream()
                .sorted(Comparator.comparing(FinalResult::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<VerticalResultDTO> verticals,
                                  Function<VerticalResultDTO, BigDecimal> amount) {
        return verticals.stream().map(amount).reduce(zero(), BigDecimal::add);
    }
}
