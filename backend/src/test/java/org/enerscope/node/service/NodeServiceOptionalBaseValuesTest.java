package org.enerscope.node.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.enerscope.node.dto.*;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.stubbing.Answer;

/**
 * Editing a node from the map never sends {@code startupDate} (and, until now, never reached
 * {@code maintenanceDuration}): an absent value must keep the stored one, a present one must update it.
 * Runs once per node type (all 12), creating each node through the same service as the editor does.
 */
class NodeServiceOptionalBaseValuesTest {
    static final Instant FIRST = Instant.parse("2030-01-01T00:00:00Z");
    static final Instant SECOND = Instant.parse("2035-06-15T00:00:00Z");

    /** save() returns what it is given, like a persistence layer would. */
    static final Answer<Object> SAVE_RETURNS_ARGUMENT = call -> "save".equals(call.getMethod().getName()) ? call.getArgument(0) : null;

    final NodeService service = new NodeService(
            mock(WellRepository.class, SAVE_RETURNS_ARGUMENT), mock(GatheringNetworkRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(TreatmentPlantRepository.class, SAVE_RETURNS_ARGUMENT), mock(PipelineRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(FLNGUnitRepository.class, SAVE_RETURNS_ARGUMENT), mock(GroundBasedLiquefactionPlantRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(LNGCarrierRepository.class, SAVE_RETURNS_ARGUMENT), mock(SeaportTerminalRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(CompressingPlantRepository.class, SAVE_RETURNS_ARGUMENT), mock(PipelineConnectionRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(NodeConnectionRepository.class, SAVE_RETURNS_ARGUMENT), mock(InternalConsumptionRepository.class, SAVE_RETURNS_ARGUMENT),
            mock(IndustrialConsumptionRepository.class, SAVE_RETURNS_ARGUMENT));

    static List<Arguments> nodeTypes() {
        return List.of(
                Arguments.of("Well", NodeTypeEnum.WELL),
                Arguments.of("GatheringNetwork", NodeTypeEnum.GATHERING_NETWORK),
                Arguments.of("TreatmentPlant", NodeTypeEnum.TREATMENT_PLANT),
                Arguments.of("Pipeline", NodeTypeEnum.PIPELINE),
                Arguments.of("PipelineConnection", NodeTypeEnum.PIPELINE_CONECTION),
                Arguments.of("CompressingPlant", NodeTypeEnum.COMPRESSING_PLANT),
                Arguments.of("GroundBasedLiquefactionPlant", NodeTypeEnum.GROUND_LIQUEFACTION_PLANT),
                Arguments.of("FLNGUnit", NodeTypeEnum.FLNG_UNIT),
                Arguments.of("SeaportTerminal", NodeTypeEnum.SEAPORT_TERMINAL),
                Arguments.of("LNGCarrier", NodeTypeEnum.LNG_CAMER),
                Arguments.of("InternalConsumption", NodeTypeEnum.INTERNAL_CONSUMPTION),
                Arguments.of("IndustrialConsumption", NodeTypeEnum.INDUSTRIAL_CONSUMPTION));
    }

    /** A DTO of the given type with every scalar filled, so the constructors and editors have what they unbox. */
    static BaseNodeDTO dto(String type, NodeTypeEnum nodeType, Instant startup, Integer maintenanceDuration) throws Exception {
        BaseNodeDTO dto = (BaseNodeDTO) Class.forName("org.enerscope.node.dto." + type + "DTO").getDeclaredConstructor().newInstance();
        for (Method setter : dto.getClass().getMethods()) {
            if (!setter.getName().startsWith("set") || setter.getParameterCount() != 1) continue;
            Class<?> parameter = setter.getParameterTypes()[0];
            Object value = parameter == String.class ? "Nodo"
                    : parameter == Instant.class ? startup
                    : parameter == Integer.class ? Integer.valueOf(1)
                    : parameter == Float.class ? Float.valueOf(1f)
                    : parameter == UUID.class ? UUID.randomUUID()
                    : parameter == NodeStateEnum.class ? NodeStateEnum.RUNNING
                    : parameter == InvestmentCostDTO.class ? investment()
                    : parameter == NodeGraphDataDTO.class ? new NodeGraphDataDTO(null, null)
                    : parameter == NodeTypeDataDTO.class ? new NodeTypeDataDTO(null, null, nodeType)
                    : parameter == ProbabilisticDistributionDTO.class ? new ProbabilisticDistributionDTO("CONSTANT", 0.5f, null)
                    : null;
            if (value != null) setter.invoke(dto, value);
        }
        dto.setStartupDate(startup);
        dto.setMaintenanceDuration(maintenanceDuration);
        return dto;
    }

    static InvestmentCostDTO investment() {
        InvestmentCostDTO cost = new InvestmentCostDTO();
        cost.setComponents(List.of());
        return cost;
    }

    BaseNode create(String type, BaseNodeDTO dto) throws Exception {
        return (BaseNode) service.getClass().getMethod("save" + type, dto.getClass()).invoke(service, dto);
    }

    BaseNode edit(String type, BaseNode node, BaseNodeDTO dto) throws Exception {
        for (Method method : service.getClass().getMethods()) {
            if (method.getName().equals("edit" + type) && method.getParameterCount() == 2) {
                try {
                    return (BaseNode) method.invoke(service, node, dto);
                } catch (java.lang.reflect.InvocationTargetException failure) {
                    if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw failure;
                }
            }
        }
        throw new AssertionError("No edit method for " + type);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeTypes")
    void creatingANodeStoresItsStartupDateAndMaintenanceDuration(String type, NodeTypeEnum nodeType) throws Exception {
        BaseNode node = create(type, dto(type, nodeType, FIRST, 5));

        assertEquals(FIRST, node.getStartupDate());
        assertEquals(5, node.getMaintenanceDuration());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeTypes")
    void editingWithoutStartupDateOrMaintenanceDurationKeepsTheStoredValues(String type, NodeTypeEnum nodeType) throws Exception {
        BaseNode node = create(type, dto(type, nodeType, FIRST, 5));

        BaseNode edited = edit(type, node, dto(type, nodeType, null, null));

        assertEquals(FIRST, edited.getStartupDate(), "the map editor sends no startupDate: it must not be erased");
        assertEquals(5, edited.getMaintenanceDuration());
        assertEquals("Nodo", edited.getName(), "the rest of the edit is still applied");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeTypes")
    void editingWithANewStartupDateOrMaintenanceDurationUpdatesThem(String type, NodeTypeEnum nodeType) throws Exception {
        BaseNode node = create(type, dto(type, nodeType, FIRST, 5));

        BaseNode edited = edit(type, node, dto(type, nodeType, SECOND, 9));

        assertEquals(SECOND, edited.getStartupDate());
        assertEquals(9, edited.getMaintenanceDuration());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("nodeTypes")
    void aNegativeMaintenanceDurationIsRejectedOnCreateAndEdit(String type, NodeTypeEnum nodeType) throws Exception {
        BaseNode node = create(type, dto(type, nodeType, FIRST, 5));

        assertThrows(Exception.class, () -> create(type, dto(type, nodeType, FIRST, -1)));
        assertThrows(IllegalArgumentException.class, () -> edit(type, node, dto(type, nodeType, FIRST, -1)));
        assertEquals(5, node.getMaintenanceDuration());
    }
}
