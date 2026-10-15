package org.enerscope.simulation;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.InvestmentCostComponent;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.export.InternalConsumption;
import org.enerscope.node.model.extraction.GatheringNetwork;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.model.transportation.Pipeline;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.version.model.Version;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Small, real (unmocked) value-chain models for the simulation tests: the
 * engine reads plain getters, so real entities with their ids set by reflection
 * behave exactly as loaded ones would.
 */
public final class SimulationFixtures {

    private SimulationFixtures() {}

    public static <T extends BaseNode> T node(T node, String name, VerticalEnum vertical, StructuralRoleEnum role,
                                              NodeTypeEnum type) {
        ReflectionTestUtils.setField(node, "id", UUID.randomUUID());
        node.setIdentityId(UUID.randomUUID());
        node.setName(name);
        node.setState(NodeStateEnum.RUNNING);
        node.setType(new NodeTypeData(vertical, role, type));
        node.setLifespanInMonths(240);
        node.setMaintenanceIntervalInDays(3650);
        node.setMaintenanceDuration(24);
        node.setOperatingCosts(MoneyAmount.of(100));
        node.setUpkeepCosts(MoneyAmount.of(0));
        node.setInvestmentCost(new InvestmentCost(new ArrayList<>(List.of(
                new InvestmentCostComponent("Obra", MoneyAmount.of(1000), CostBasisEnum.FLAT)))));
        return node;
    }

    public static Well well(String name, float capacity) {
        Well well = node(new Well(), name, VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL);
        well.setMaxCollectionCapacity(capacity);
        well.setDeclineCurve(new ConstantValue(0f));
        well.setDTMTime(24);
        return well;
    }

    public static GatheringNetwork gathering(String name) {
        GatheringNetwork network = node(new GatheringNetwork(), name, VerticalEnum.EXTRACTION,
                StructuralRoleEnum.INTERMEDIATE, NodeTypeEnum.GATHERING_NETWORK);
        network.setMaxTransportCapacity(1000f);
        network.setLength(10f);
        network.setLossPerMeter(1f);   // 10 % of what it carries
        return network;
    }

    public static Pipeline pipeline(String name) {
        Pipeline pipeline = node(new Pipeline(), name, VerticalEnum.TRANSPORTATION,
                StructuralRoleEnum.INTERMEDIATE, NodeTypeEnum.PIPELINE);
        pipeline.setMaxFlowCapacity(1000f);
        pipeline.setLength(1f);
        pipeline.setLossPerKm(0f);
        return pipeline;
    }

    public static InternalConsumption consumption(String name, float perHour) {
        InternalConsumption consumption = node(new InternalConsumption(), name, VerticalEnum.INTERNAL_CONSUMPTION,
                StructuralRoleEnum.SINK, NodeTypeEnum.INTERNAL_CONSUMPTION);
        consumption.setConsumptionSummer(perHour);
        consumption.setConsumptionAutumn(perHour);
        consumption.setConsumptionWinter(perHour);
        consumption.setConsumptionSpring(perHour);
        return consumption;
    }

    public static NodeConnection connect(BaseNode from, BaseNode to) {
        NodeConnection connection = new NodeConnection(UUID.randomUUID(), from.getId(), to.getId());
        ReflectionTestUtils.setField(connection, "id", UUID.randomUUID());
        return connection;
    }

    public static Version version(String name, List<BaseNode> nodes, List<NodeConnection> connections) {
        Version version = new Version(name, null, new ArrayList<>(nodes), new ArrayList<>(connections), null, null);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }

    /** Well (100/h) → gathering network (10 % loss) → domestic consumption (1000/h). */
    public static Version domesticChain(String name) {
        Well well = well("Pozo 1", 100f);
        GatheringNetwork network = gathering("Red 1");
        InternalConsumption consumption = consumption("Consumo", 1000f);
        return version(name, List.of(well, network, consumption),
                List.of(connect(well, network), connect(network, consumption)));
    }
}
