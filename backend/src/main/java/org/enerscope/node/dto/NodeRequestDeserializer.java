package org.enerscope.node.dto;

import java.io.IOException;
import java.util.Map;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

/** Routes node requests by their explicit nested discriminator, never by field overlap. */
public final class NodeRequestDeserializer extends StdDeserializer<BaseNodeDTO> {
    private static final Map<String, Class<? extends BaseNodeDTO>> TYPES = Map.of(
        "WELL", WellDTO.class,
        "GATHERING_NETWORK", GatheringNetworkDTO.class,
        "TREATMENT_PLANT", TreatmentPlantDTO.class,
        "PIPELINE", PipelineDTO.class,
        "COMPRESSING_PLANT", CompressingPlantDTO.class,
        "GROUND_LIQUEFACTION_PLANT", GroundBasedLiquefactionPlantDTO.class,
        "FLNG_UNIT", FLNGUnitDTO.class,
        "SEAPORT_TERMINAL", SeaportTerminalDTO.class,
        "LNG_CAMER", LNGCarrierDTO.class);

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
