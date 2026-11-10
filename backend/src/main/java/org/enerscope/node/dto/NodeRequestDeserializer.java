package org.enerscope.node.dto;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/** Routes node requests by their explicit nested discriminator, never by field overlap. */
public final class NodeRequestDeserializer extends StdDeserializer<BaseNodeDTO> {
    private static final Map<String, Class<? extends BaseNodeDTO>> TYPES = Map.ofEntries(
        Map.entry("WELL", WellDTO.class),
        Map.entry("GATHERING_NETWORK", GatheringNetworkDTO.class),
        Map.entry("TREATMENT_PLANT", TreatmentPlantDTO.class),
        Map.entry("PIPELINE", PipelineDTO.class),
        Map.entry("COMPRESSING_PLANT", CompressingPlantDTO.class),
        Map.entry("GROUND_LIQUEFACTION_PLANT", GroundBasedLiquefactionPlantDTO.class),
        Map.entry("FLNG_UNIT", FLNGUnitDTO.class),
        Map.entry("SEAPORT_TERMINAL", SeaportTerminalDTO.class),
        Map.entry("LNG_CAMER", LNGCarrierDTO.class),
        Map.entry("INTERNAL_CONSUMPTION", InternalConsumptionDTO.class),
        Map.entry("INDUSTRIAL_CONSUMPTION", IndustrialConsumptionDTO.class));

    public NodeRequestDeserializer() { super(BaseNodeDTO.class); }

    @Override
    public BaseNodeDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode body = parser.getCodec().readTree(parser);
        JsonNode discriminator = body.path("type").path("nodeType");
        Class<? extends BaseNodeDTO> target = discriminator.isTextual() ? TYPES.get(discriminator.textValue()) : null;
        if (target == null)
            return context.reportInputMismatch(BaseNodeDTO.class, "A supported type.nodeType is required");
        return parser.getCodec().treeToValue(body, target);
    }
}
