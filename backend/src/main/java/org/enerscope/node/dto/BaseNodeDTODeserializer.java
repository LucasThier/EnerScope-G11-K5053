package org.enerscope.node.dto;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.enerscope.node.model.enums.NodeTypeEnum;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Picks the concrete node DTO for a payload.
 *
 * <p>The subtype used to be deduced from which fields were present
 * ({@code JsonTypeInfo.Id.DEDUCTION}). That cannot work for a ground-based
 * liquefaction plant: its fields are a subset of an FLNG unit's, so its payload
 * matched both and every create from the editor failed with "malformed request
 * body". The payload already says what it is — {@code type.nodeType}, which the
 * node stores anyway — so that decides. Deduction is kept only as the fallback
 * for a payload without it, and resolves the subset case by choosing the
 * <em>most specific</em> match (the candidate with the fewest fields that still
 * has all of the payload's).</p>
 *
 * <p>Every subclass resets {@code @JsonDeserialize} to the default: class-level
 * Jackson annotations are inherited, and without the reset deserializing the
 * chosen subclass would come straight back here.</p>
 */
public class BaseNodeDTODeserializer extends StdDeserializer<BaseNodeDTO> {

    static final Map<NodeTypeEnum, Class<? extends BaseNodeDTO>> BY_NODE_TYPE = new EnumMap<>(NodeTypeEnum.class);

    static {
        BY_NODE_TYPE.put(NodeTypeEnum.WELL, WellDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.GATHERING_NETWORK, GatheringNetworkDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.TREATMENT_PLANT, TreatmentPlantDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.PIPELINE, PipelineDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.PIPELINE_CONECTION, PipelineConnectionDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.COMPRESSING_PLANT, CompressingPlantDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.GROUND_LIQUEFACTION_PLANT, GroundBasedLiquefactionPlantDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.FLNG_UNIT, FLNGUnitDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.SEAPORT_TERMINAL, SeaportTerminalDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.LNG_CAMER, LNGCarrierDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.INTERNAL_CONSUMPTION, InternalConsumptionDTO.class);
        BY_NODE_TYPE.put(NodeTypeEnum.INDUSTRIAL_CONSUMPTION, IndustrialConsumptionDTO.class);
    }

    public BaseNodeDTODeserializer() {
        super(BaseNodeDTO.class);
    }

    @Override
    public BaseNodeDTO deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonNode tree = parser.readValueAsTree();
        if (!(tree instanceof ObjectNode object)) {
            return (BaseNodeDTO) context.handleUnexpectedToken(BaseNodeDTO.class, parser);
        }
        Class<? extends BaseNodeDTO> target = byNodeType(object, context);
        if (target == null) {
            target = byFields(object, context);
        }
        return context.readTreeAsValue(object, target);
    }

    private static Class<? extends BaseNodeDTO> byNodeType(ObjectNode object, DeserializationContext context)
            throws IOException {
        JsonNode nodeType = object.path("type").path("nodeType");
        if (!nodeType.isTextual()) {
            return null;
        }
        try {
            return BY_NODE_TYPE.get(NodeTypeEnum.valueOf(nodeType.asText()));
        } catch (IllegalArgumentException e) {
            return context.reportInputMismatch(BaseNodeDTO.class, "Unknown node type '%s'", nodeType.asText());
        }
    }

    private static Class<? extends BaseNodeDTO> byFields(ObjectNode object, DeserializationContext context)
            throws IOException {
        Set<String> present = new HashSet<>();
        for (Iterator<String> names = object.fieldNames(); names.hasNext(); ) {
            present.add(names.next());
        }
        List<Class<? extends BaseNodeDTO>> best = new ArrayList<>();
        int bestSize = Integer.MAX_VALUE;
        for (Class<? extends BaseNodeDTO> candidate : new HashSet<>(BY_NODE_TYPE.values())) {
            Set<String> properties = new HashSet<>();
            for (BeanPropertyDefinition property
                    : context.getConfig().introspect(context.constructType(candidate)).findProperties()) {
                properties.add(property.getName());
            }
            if (!properties.containsAll(present)) {
                continue;
            }
            if (properties.size() < bestSize) {
                best.clear();
                bestSize = properties.size();
            }
            if (properties.size() == bestSize) {
                best.add(candidate);
            }
        }
        if (best.size() != 1) {
            return context.reportInputMismatch(BaseNodeDTO.class,
                    "Cannot tell the node type from the payload; send type.nodeType");
        }
        return best.get(0);
    }
}
