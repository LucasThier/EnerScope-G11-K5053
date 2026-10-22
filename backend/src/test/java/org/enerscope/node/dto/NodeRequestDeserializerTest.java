package org.enerscope.node.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NodeRequestDeserializerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void everySupportedNodeTypeSelectsItsDeclaredClass() throws Exception {
        String[] types = {"WELL", "GATHERING_NETWORK", "TREATMENT_PLANT", "PIPELINE", "COMPRESSING_PLANT", "GROUND_LIQUEFACTION_PLANT", "FLNG_UNIT", "SEAPORT_TERMINAL", "LNG_CAMER"};
        Class<?>[] classes = {WellDTO.class, GatheringNetworkDTO.class, TreatmentPlantDTO.class, PipelineDTO.class, CompressingPlantDTO.class, GroundBasedLiquefactionPlantDTO.class, FLNGUnitDTO.class, SeaportTerminalDTO.class, LNGCarrierDTO.class};
        for (int i = 0; i < types.length; i++) {
            var body = mapper.createObjectNode(); body.putObject("type").put("nodeType", types[i]);
            assertEquals(classes[i], mapper.treeToValue(body, BaseNodeDTO.class).getClass());
        }
    }

    @Test
    void overlappingPlantFieldsRespectDiscriminatorRegardlessOfOrder() throws Exception {
        for (String type : new String[]{"GROUND_LIQUEFACTION_PLANT", "FLNG_UNIT"}) {
            String fields = "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"gasConsumption\":30";
            String discriminator = "\"type\":{\"nodeType\":\"" + type + "\"}";
            for (String json : new String[]{"{" + fields + "," + discriminator + "}", "{" + discriminator + "," + fields + "}"}) {
                var dto = mapper.readValue(json, BaseNodeDTO.class);
                if (type.equals("FLNG_UNIT")) { assertInstanceOf(FLNGUnitDTO.class, dto); assertEquals(140f, ((FLNGUnitDTO)dto).getMaxProcessingCapacity()); }
                else { assertInstanceOf(GroundBasedLiquefactionPlantDTO.class, dto); assertEquals(30f, ((GroundBasedLiquefactionPlantDTO)dto).getGasConsumption()); }
            }
        }
    }

    @Test
    void missingMalformedUnknownAndUnsupportedTypesAreRejected() {
        for (String json : new String[]{"{}", "{\"type\":null}", "{\"type\":{\"nodeType\":12}}", "{\"type\":{\"nodeType\":\"UNKNOWN\"}}", "{\"type\":{\"nodeType\":\"INTERNAL_CONSUMPTION\"}}"})
            assertThrows(com.fasterxml.jackson.databind.JsonMappingException.class, () -> mapper.readValue(json, BaseNodeDTO.class));
    }
}
