package org.enerscope.economic.service;
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.enerscope.economic.dto.FrozenEconomicInput;
import org.enerscope.economic.dto.FrozenEconomicInput.PhysicalNode;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.model.results.SimulationMetadata;
import org.enerscope.node.model.*;
import org.enerscope.node.model.extraction.*;
import org.enerscope.node.model.export.*;
import org.enerscope.node.model.liquefaction.*;
import org.enerscope.node.model.transportation.*;
import org.enerscope.probabilistic.*;
import org.enerscope.simulator.Simulator;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Service;

/** Explicit type allow-list and field serialization preserve actual inputs, not computed entity getters. */
@Service
public class EconomicInputCodec {
    private final ObjectMapper mapper;

    private static final List<Class<? extends BaseNode>> TYPES = List.of(Well.class,
            GatheringNetwork.class,
            TreatmentPlant.class,
            Pipeline.class,
            PipelineConnection.class,
            CompressingPlant.class,
            GroundBasedLiquefactionPlant.class,
            FLNGUnit.class,
            SeaportTerminal.class,
            LNGCarrier.class,
            InternalConsumption.class,
            IndustrialConsumption.class);

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "distributionType")
    @JsonSubTypes({@JsonSubTypes.Type(value = ConstantValue.class, name = "CONSTANT"),
        @JsonSubTypes.Type(value = NormalDistributionCase.class, name = "NORMAL"),
        @JsonSubTypes.Type(value = UniformDistributionCase.class, name = "UNIFORM")})
    private abstract static class DistributionType {}

    public EconomicInputCodec(ObjectMapper original) {
        mapper = original.copy().setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.NONE)
                .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.addMixIn(ProbabilisticDistribution.class, DistributionType.class);
    }

    public FrozenEconomicInput freeze(Version version, EconomicConfiguration configuration, int rounds, long seed) {
        List<PhysicalNode> nodes = version.getNodeSnapshot().stream().map(node -> {
            Class<? extends BaseNode> type = TYPES.stream().filter(t -> t.isInstance(node)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported physical node"));
            return new PhysicalNode(type.getSimpleName(), mapper.valueToTree(node));
        }).toList();
        var input = new FrozenEconomicInput(configuration, version.getName(), nodes,
                mapper.valueToTree(version.getConnectionSnapshot()),
                new SimulationMetadata(rounds, Long.toString(seed), Simulator.ALGORITHM, Simulator.CALCULATION_VERSION));
        // Validate reconstruction and graph before accepting any background work.
        new Simulator(thaw(input), configuration.startYear() + 1, Simulator.seedForRound(seed, 0));
        return input;
    }

    public Version thaw(FrozenEconomicInput input) {
        if (!Simulator.ALGORITHM.equals(input.simulation().algorithm())
                || input.simulation().calculationVersion() != Simulator.CALCULATION_VERSION)
            throw new IllegalArgumentException("Unsupported simulation version");
        List<BaseNode> nodes = input.nodes().stream().map(n -> {
            var type = TYPES.stream().filter(t -> t.getSimpleName().equals(n.type())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unsupported physical type"));
            return (BaseNode) mapper.convertValue(n.data(), type);
        }).toList();
        var connections = Arrays.asList(mapper.convertValue(input.connections(), NodeConnection[].class));
        return new Version(input.name(), null, nodes, connections, new ArrayList<>(), new ArrayList<>());
    }

    public JsonNode displayNodes(FrozenEconomicInput input) {
        return mapper.valueToTree(input.nodes().stream().map(PhysicalNode::data).toList());
    }
}
