package org.enerscope.version.service;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.enerscope.common.CsvUtil;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.CompressingPlantDTO;
import org.enerscope.node.dto.FLNGUnitDTO;
import org.enerscope.node.dto.GatheringNetworkDTO;
import org.enerscope.node.dto.GroundBasedLiquefactionPlantDTO;
import org.enerscope.node.dto.InvestmentCostComponentDTO;
import org.enerscope.node.dto.IndustrialConsumptionDTO;
import org.enerscope.node.dto.InternalConsumptionDTO;
import org.enerscope.node.dto.LNGCarrierDTO;
import org.enerscope.node.dto.PipelineConnectionDTO;
import org.enerscope.node.dto.PipelineDTO;
import org.enerscope.node.dto.SeaportTerminalDTO;
import org.enerscope.node.dto.TreatmentPlantDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.project.service.ProjectAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class VersionNodeCsvImportService {

    public static final long MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024;
    public static final int MAX_DATA_ROWS = 10_000;
    private static final String NODE_KIND_HEADER = "nodeKind";
    private static final Set<String> JSON_HEADERS = Set.of(
            "declineCurve", "graphData", "investmentCost", "type");

    private final VersionService versionService;
    private final ProjectAccessGuard accessGuard;
    private final ObjectMapper objectMapper;
    private final AppLogger logger;
    private final Set<String> knownHeaders;

    public VersionNodeCsvImportService(
            VersionService versionService,
            ProjectAccessGuard accessGuard,
            ObjectMapper objectMapper,
            AppLogger logger) {
        this.versionService = versionService;
        this.accessGuard = accessGuard;
        this.objectMapper = objectMapper;
        this.logger = logger;
        this.knownHeaders = discoverKnownHeaders();
    }

    @Transactional
    public int importNodes(UUID versionId, byte[] csvBytes) {
        if (versionId == null) {
            throw new IllegalArgumentException("Version ID cannot be null");
        }
        if (csvBytes == null || csvBytes.length == 0) {
            throw new IllegalArgumentException("CSV file cannot be empty");
        }
        accessGuard.assertCanEditVersion(versionId);
        if (csvBytes.length > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("CSV file must be 5 MB or smaller");
        }

        String csvContent;
        try {
            csvContent = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(csvBytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new IllegalArgumentException("CSV file must be valid UTF-8", exception);
        }

        List<List<String>> rows = CsvUtil.parseStrict(csvContent, MAX_DATA_ROWS + 1);
        if (rows.size() < 2) {
            throw new IllegalArgumentException("CSV must contain a header and at least one data row");
        }

        List<String> headers = normalizeHeaders(rows.get(0));
        validateHeaders(headers);

        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            int recordNumber = rowIndex + 1;
            if (row.size() != headers.size()) {
                throw new IllegalArgumentException(
                        "CSV record %d has %d fields; expected %d".formatted(recordNumber, row.size(), headers.size()));
            }

            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 0; column < headers.size(); column++) {
                values.put(headers.get(column), row.get(column));
            }

            NodeKind nodeKind = resolveNodeKind(values.get(NODE_KIND_HEADER), recordNumber);
            validateRequiredColumns(nodeKind, values, recordNumber);
            BaseNodeDTO node = toNodeDto(nodeKind, headers, values, recordNumber);
            validateNode(node, nodeKind, recordNumber);
            try {
                versionService.addNodeToVersion(versionId, node);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid CSV record %d (%s): %s".formatted(recordNumber, nodeKind, exception.getMessage()),
                        exception);
            }
        }

        int importedCount = rows.size() - 1;
        logger.info("Imported {} nodes into version {}", importedCount, versionId);
        return importedCount;
    }

    private Set<String> discoverKnownHeaders() {
        Set<String> headers = new LinkedHashSet<>();
        headers.add(NODE_KIND_HEADER);
        for (NodeKind nodeKind : NodeKind.values()) {
            BeanDescription description = objectMapper.getDeserializationConfig()
                    .introspect(objectMapper.constructType(nodeKind.dtoClass));
            for (BeanPropertyDefinition property : description.findProperties()) {
                headers.add(property.getName());
            }
        }
        return Set.copyOf(headers);
    }

    private List<String> normalizeHeaders(List<String> rawHeaders) {
        if (rawHeaders.isEmpty()) {
            throw new IllegalArgumentException("CSV header row cannot be empty");
        }
        List<String> headers = new ArrayList<>(rawHeaders);
        if (headers.get(0).startsWith("\uFEFF")) {
            headers.set(0, headers.get(0).substring(1));
        }
        if (headers.stream().anyMatch(String::isEmpty)) {
            throw new IllegalArgumentException("CSV headers cannot be blank");
        }
        if (new HashSet<>(headers).size() != headers.size()) {
            throw new IllegalArgumentException("CSV headers must be unique");
        }
        return headers;
    }

    private void validateHeaders(List<String> headers) {
        List<String> unknownHeaders = headers.stream()
                .filter(header -> !knownHeaders.contains(header))
                .toList();
        if (!unknownHeaders.isEmpty()) {
            throw new IllegalArgumentException("Unknown CSV header(s): " + String.join(", ", unknownHeaders));
        }
        if (!headers.contains(NODE_KIND_HEADER)) {
            throw new IllegalArgumentException("CSV must contain a 'nodeKind' header");
        }
    }

    private NodeKind resolveNodeKind(String value, int recordNumber) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CSV record %d is missing nodeKind".formatted(recordNumber));
        }
        try {
            return NodeKind.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            String supportedKinds = java.util.Arrays.stream(NodeKind.values())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "Unsupported nodeKind '%s' in CSV record %d. Supported values: %s"
                            .formatted(value, recordNumber, supportedKinds));
        }
    }

    private BaseNodeDTO toNodeDto(
            NodeKind nodeKind,
            List<String> headers,
            Map<String, String> values,
            int recordNumber) {
        ObjectNode json = objectMapper.createObjectNode();
        Set<String> supportedHeaders = propertiesFor(nodeKind);

        for (String header : headers) {
            if (NODE_KIND_HEADER.equals(header)) {
                continue;
            }
            String value = values.get(header);
            if (!supportedHeaders.contains(header)) {
                if (!value.isBlank()) {
                    throw new IllegalArgumentException(
                            "CSV record %d (%s) has a value in unsupported column '%s'"
                                    .formatted(recordNumber, nodeKind, header));
                }
                continue;
            }
            if (value.isBlank()) {
                continue;
            }
            if (JSON_HEADERS.contains(header)) {
                try {
                    JsonNode nestedValue = objectMapper.readTree(value);
                    if (nestedValue == null || !nestedValue.isObject()) {
                        throw new IllegalArgumentException(
                                "CSV record %d column '%s' must contain a JSON object"
                                        .formatted(recordNumber, header));
                    }
                    json.set(header, nestedValue);
                } catch (JsonProcessingException exception) {
                    throw new IllegalArgumentException(
                            "CSV record %d has invalid JSON in column '%s'"
                                    .formatted(recordNumber, header),
                            exception);
                }
            } else {
                json.put(header, value);
            }
        }
        validateNestedJson(nodeKind, json, recordNumber);

        try {
            ObjectMapper concreteNodeMapper = objectMapper.copy();
            concreteNodeMapper.addMixIn(nodeKind.dtoClass, ConcreteNodeDtoMixin.class);
            BaseNodeDTO node = concreteNodeMapper.treeToValue(json, nodeKind.dtoClass);
            validateFiniteScalarNumbers(node, headers, values, nodeKind, recordNumber);
            return node;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(
                    "CSV record %d (%s) contains an invalid value: %s"
                            .formatted(recordNumber, nodeKind, exception.getOriginalMessage()),
                    exception);
        }
    }

    private Set<String> propertiesFor(NodeKind nodeKind) {
        BeanDescription description = objectMapper.getDeserializationConfig()
                .introspect(objectMapper.constructType(nodeKind.dtoClass));
        return description.findProperties().stream()
                .map(BeanPropertyDefinition::getName)
                .collect(Collectors.toSet());
    }

    private void validateRequiredColumns(NodeKind nodeKind, Map<String, String> values, int recordNumber) {
        List<String> required = new ArrayList<>(List.of(
                "name", "state", "lifespanInMonths", "upkeepCosts", "maintenanceIntervalInDays",
                "operatingCosts", "wastePercentage", "type", "investmentCost"));
        required.addAll(nodeKind.requiredFields);
        for (String field : required) {
            if (values.get(field) == null || values.get(field).isBlank()) {
                throw invalidRequiredField(recordNumber, nodeKind, field);
            }
        }
    }

    private void validateNestedJson(NodeKind nodeKind, ObjectNode json, int recordNumber) {
        if (json.has("type")) {
            JsonNode type = json.get("type");
            if (type.isObject()) {
                rejectUnknownFields(type, Set.of("vertical", "role", "nodeType"), "type", recordNumber);
                requireJsonEnum(type, "vertical", VerticalEnum.class, nodeKind, recordNumber);
                requireJsonEnum(type, "role", StructuralRoleEnum.class, nodeKind, recordNumber);
                NodeTypeEnum nodeType = requireJsonEnum(type, "nodeType", NodeTypeEnum.class, nodeKind, recordNumber);
                if (nodeType != nodeKind.expectedNodeType) {
                    throw new IllegalArgumentException(
                            "CSV record %d (%s) has type.nodeType '%s'; expected '%s'"
                                    .formatted(recordNumber, nodeKind, nodeType, nodeKind.expectedNodeType));
                }
                VerticalEnum vertical = VerticalEnum.valueOf(type.get("vertical").asText());
                if (vertical != nodeKind.expectedVertical) {
                    throw new IllegalArgumentException(
                            "CSV record %d (%s) has type.vertical '%s'; expected '%s'"
                                    .formatted(recordNumber, nodeKind, vertical, nodeKind.expectedVertical));
                }
            }
        }

        if (json.has("declineCurve")) {
            if (json.get("declineCurve").isObject()) {
                rejectUnknownFields(json.get("declineCurve"), Set.of("type", "val_a", "val_b"),
                        "declineCurve", recordNumber);
            }
            validateDistribution(json.get("declineCurve"), nodeKind, recordNumber);
        }
        if (json.has("investmentCost")) {
            JsonNode investmentCost = json.get("investmentCost");
            rejectUnknownFields(investmentCost, Set.of("components"), "investmentCost", recordNumber);
            JsonNode components = investmentCost.get("components");
            if (components != null && components.isArray()) {
                for (int i = 0; i < components.size(); i++) {
                    JsonNode component = components.get(i);
                    if (!component.isObject()) {
                        throw new IllegalArgumentException(
                                "CSV record %d investmentCost.components[%d] must be an object"
                                        .formatted(recordNumber, i));
                    }
                    rejectUnknownFields(component, Set.of("name", "amount", "costBasis"),
                            "investmentCost.components[%d]".formatted(i), recordNumber);
                    requireJsonField(component, "name", JsonNode::isTextual, nodeKind, recordNumber);
                    requireJsonField(component, "amount", JsonNode::isNumber, nodeKind, recordNumber);
                    requireJsonEnum(component, "costBasis", CostBasisEnum.class, nodeKind, recordNumber);
                    if (!Double.isFinite(component.get("amount").doubleValue())) {
                        throw invalidNumericField(recordNumber, "investmentCost.components[%d].amount".formatted(i));
                    }
                }
            }
        }
        if (json.has("graphData")) {
            validateGraphData(json.get("graphData"), recordNumber);
        }
    }

    private void validateGraphData(JsonNode graphData, int recordNumber) {
        if (!graphData.isObject()) {
            throw new IllegalArgumentException("CSV record %d graphData must be an object".formatted(recordNumber));
        }
        rejectUnknownFields(graphData, Set.of("graphPosition", "geographicalPosition"), "graphData", recordNumber);
        JsonNode graphPosition = graphData.get("graphPosition");
        if (graphPosition != null && !graphPosition.isNull()) {
            rejectUnknownFields(graphPosition, Set.of("x", "y"), "graphData.graphPosition", recordNumber);
            requireFinitePair(graphPosition, "x", "y", "graphData.graphPosition", recordNumber);
        }
        JsonNode geographicalPosition = graphData.get("geographicalPosition");
        if (geographicalPosition != null && !geographicalPosition.isNull()) {
            rejectUnknownFields(geographicalPosition, Set.of("longitude", "latitude"),
                    "graphData.geographicalPosition", recordNumber);
            requireFinitePair(geographicalPosition, "longitude", "latitude",
                    "graphData.geographicalPosition", recordNumber);
            double longitude = geographicalPosition.get("longitude").doubleValue();
            double latitude = geographicalPosition.get("latitude").doubleValue();
            if (longitude < -180 || longitude > 180 || latitude < -90 || latitude > 90) {
                throw new IllegalArgumentException(
                        "CSV record %d geographicalPosition must use longitude [-180, 180] "
                                + "and latitude [-90, 90]".formatted(recordNumber));
            }
        }
    }

    private void requireFinitePair(
            JsonNode object,
            String firstField,
            String secondField,
            String fieldName,
            int recordNumber) {
        if (!object.isObject()
                || !object.has(firstField)
                || !object.has(secondField)
                || !object.get(firstField).isNumber()
                || !object.get(secondField).isNumber()
                || !Double.isFinite(object.get(firstField).doubleValue())
                || !Double.isFinite(object.get(secondField).doubleValue())) {
            throw new IllegalArgumentException(
                    "CSV record %d %s must contain finite numeric '%s' and '%s' values"
                            .formatted(recordNumber, fieldName, firstField, secondField));
        }
    }

    private void rejectUnknownFields(JsonNode object, Set<String> allowedFields, String fieldName, int recordNumber) {
        if (!object.isObject()) {
            throw new IllegalArgumentException("CSV record %d %s must be a JSON object"
                    .formatted(recordNumber, fieldName));
        }
        object.fieldNames().forEachRemaining(field -> {
            if (!allowedFields.contains(field)) {
                throw new IllegalArgumentException(
                        "CSV record %d has unknown field '%s.%s'"
                                .formatted(recordNumber, fieldName, field));
            }
        });
    }

    private void validateDistribution(JsonNode distribution, NodeKind nodeKind, int recordNumber) {
        if (!distribution.isObject()) {
            throw new IllegalArgumentException(
                    "CSV record %d declineCurve must be a JSON object".formatted(recordNumber));
        }
        JsonNode typeNode = distribution.get("type");
        if (typeNode == null || !typeNode.isTextual()) {
            throw new IllegalArgumentException(
                    "CSV record %d declineCurve.type is required".formatted(recordNumber));
        }
        String type = typeNode.asText().toUpperCase(Locale.ROOT);
        if (!Set.of("CONSTANT", "UNIFORM", "NORMAL").contains(type)) {
            throw new IllegalArgumentException(
                    "CSV record %d has unsupported declineCurve.type '%s'".formatted(recordNumber, type));
        }
        JsonNode valueA = distribution.get("val_a");
        if (valueA == null || !valueA.isNumber() || !Double.isFinite(valueA.doubleValue())) {
            throw new IllegalArgumentException(
                    "CSV record %d declineCurve.val_a must be a finite number".formatted(recordNumber));
        }
        if ("UNIFORM".equals(type) || "NORMAL".equals(type)) {
            JsonNode valueB = distribution.get("val_b");
            if (valueB == null || !valueB.isNumber() || !Double.isFinite(valueB.doubleValue())) {
                throw new IllegalArgumentException(
                        "CSV record %d declineCurve.val_b must be a finite number for %s"
                                .formatted(recordNumber, type));
            }
            if ("UNIFORM".equals(type) && valueA.doubleValue() >= valueB.doubleValue()) {
                throw new IllegalArgumentException(
                        "CSV record %d declineCurve uniform val_a must be less than val_b"
                                .formatted(recordNumber));
            }
            if ("NORMAL".equals(type) && valueB.doubleValue() <= 0) {
                throw new IllegalArgumentException(
                        "CSV record %d declineCurve normal val_b must be greater than zero"
                                .formatted(recordNumber));
            }
        }
        ((ObjectNode) distribution).put("type", type);
    }

    private <E extends Enum<E>> E requireJsonEnum(
            JsonNode object,
            String field,
            Class<E> enumType,
            NodeKind nodeKind,
            int recordNumber) {
        requireJsonField(object, field, JsonNode::isTextual, nodeKind, recordNumber);
        String value = object.get(field).asText();
        try {
            return Enum.valueOf(enumType, value);
        } catch (IllegalArgumentException exception) {
            String allowed = java.util.Arrays.stream(enumType.getEnumConstants())
                    .map(Enum::name)
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                    "CSV record %d (%s) has invalid %s '%s'. Allowed values: %s"
                            .formatted(recordNumber, nodeKind, field, value, allowed));
        }
    }

    private void requireJsonField(
            JsonNode object,
            String field,
            java.util.function.Predicate<JsonNode> valueCheck,
            NodeKind nodeKind,
            int recordNumber) {
        JsonNode value = object.get(field);
        if (value == null || !valueCheck.test(value)) {
            throw invalidRequiredField(recordNumber, nodeKind, field);
        }
    }

    private void validateFiniteScalarNumbers(
            BaseNodeDTO node,
            List<String> headers,
            Map<String, String> values,
            NodeKind nodeKind,
            int recordNumber) {
        BeanDescription description = objectMapper.getDeserializationConfig()
                .introspect(objectMapper.constructType(nodeKind.dtoClass));
        Map<String, Class<?>> numericProperties = description.findProperties().stream()
                .filter(property -> isNumericType(property.getPrimaryType().getRawClass()))
                .collect(Collectors.toMap(BeanPropertyDefinition::getName,
                        property -> property.getPrimaryType().getRawClass()));
        for (String header : headers) {
            Class<?> numericType = numericProperties.get(header);
            String value = values.get(header);
            if (numericType == null || value == null || value.isBlank()) {
                continue;
            }
            try {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw invalidNumericField(recordNumber, header);
                }
                if ((numericType == float.class || numericType == Float.class)
                        && Math.abs(parsed) > Float.MAX_VALUE) {
                    throw invalidNumericField(recordNumber, header);
                }
                if ((numericType == int.class || numericType == Integer.class
                        || numericType == long.class || numericType == Long.class
                        || numericType == short.class || numericType == Short.class)
                        && (parsed != Math.rint(parsed)
                                || !isInIntegerRange(parsed, numericType))) {
                    throw invalidNumericField(recordNumber, header);
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "CSV record %d column '%s' must contain a valid number".formatted(recordNumber, header),
                        exception);
            }
        }
    }

    private boolean isNumericType(Class<?> type) {
        return type == int.class || type == Integer.class
                || type == float.class || type == Float.class
                || type == double.class || type == Double.class
                || type == long.class || type == Long.class
                || type == short.class || type == Short.class;
    }

    private boolean isInIntegerRange(double value, Class<?> numericType) {
        if (numericType == long.class || numericType == Long.class) {
            return value >= Long.MIN_VALUE && value <= Long.MAX_VALUE;
        }
        if (numericType == short.class || numericType == Short.class) {
            return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
        }
        return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
    }

    private IllegalArgumentException invalidNumericField(int recordNumber, String field) {
        return new IllegalArgumentException(
                "CSV record %d column '%s' must contain a finite number".formatted(recordNumber, field));
    }

    private void validateNode(BaseNodeDTO node, NodeKind nodeKind, int recordNumber) {
        if (node.getName() == null || node.getName().isBlank()) {
            throw invalidRequiredField(recordNumber, nodeKind, "name");
        }
        requireField(node.getState(), "state", recordNumber, nodeKind);
        requireField(node.getLifespanInMonths(), "lifespanInMonths", recordNumber, nodeKind);
        requireField(node.getUpkeepCosts(), "upkeepCosts", recordNumber, nodeKind);
        requireField(node.getMaintenanceIntervalInDays(), "maintenanceIntervalInDays", recordNumber, nodeKind);
        requireField(node.getOperatingCosts(), "operatingCosts", recordNumber, nodeKind);
        requireField(node.getWastePercentage(), "wastePercentage", recordNumber, nodeKind);
        requireField(node.getType(), "type", recordNumber, nodeKind);
        if (node.getType().getVertical() == null
                || node.getType().getRole() == null
                || node.getType().getNodeType() == null) {
            throw invalidRequiredField(recordNumber, nodeKind, "type.vertical, type.role and type.nodeType");
        }
        if (node.getInvestmentCost() == null || node.getInvestmentCost().getComponents() == null) {
            throw invalidRequiredField(recordNumber, nodeKind, "investmentCost.components");
        }
        for (InvestmentCostComponentDTO component : node.getInvestmentCost().getComponents()) {
            if (component == null || component.getName() == null || component.getName().isBlank()
                    || component.getAmount() == null || component.getCostBasis() == null) {
                throw invalidRequiredField(
                        recordNumber, nodeKind, "investmentCost.components.name, amount and costBasis");
            }
        }
        validateTypeConsistency(node, nodeKind, recordNumber);
        switch (node) {
            case WellDTO well -> {
                requireField(well.getMaxCollectionCapacity(), "maxCollectionCapacity", recordNumber, nodeKind);
                requireField(well.getDeclineCurve(), "declineCurve", recordNumber, nodeKind);
                requireField(well.getGasRichness(), "gasRichness", recordNumber, nodeKind);
                requireField(well.getDTMCost(), "dtmCost", recordNumber, nodeKind);
                requireField(well.getSurface(), "surface", recordNumber, nodeKind);
            }
            case PipelineDTO pipeline -> {
                requireField(pipeline.getMaxFlowCapacity(), "maxFlowCapacity", recordNumber, nodeKind);
                requireField(pipeline.getLength(), "length", recordNumber, nodeKind);
                requireField(pipeline.getLossPerKm(), "lossPerKm", recordNumber, nodeKind);
            }
            case PipelineConnectionDTO connection -> {
                requireField(connection.getTransferCapacity(), "transferCapacity", recordNumber, nodeKind);
                requireField(connection.getOutputPriority(), "outputPriority", recordNumber, nodeKind);
            }
            case CompressingPlantDTO plant -> {
                requireField(plant.getMaxCompressionCapacity(), "maxCompressionCapacity", recordNumber, nodeKind);
                requireField(plant.getProcessWaste(), "processWaste", recordNumber, nodeKind);
                requireField(plant.getGasConsumption(), "gasConsumption", recordNumber, nodeKind);
            }
            case GroundBasedLiquefactionPlantDTO plant -> {
                requireField(plant.getMaxProcessingCapacity(), "maxProcessingCapacity", recordNumber, nodeKind);
                requireField(plant.getMTPARatio(), "mtpaRatio", recordNumber, nodeKind);
                requireField(plant.getIntermediateStorage(), "intermediateStorage", recordNumber, nodeKind);
                requireField(plant.getGasConsumption(), "gasConsumption", recordNumber, nodeKind);
            }
            case FLNGUnitDTO unit -> {
                requireField(unit.getMaxProcessingCapacity(), "maxProcessingCapacity", recordNumber, nodeKind);
                requireField(unit.getMTPARatio(), "mtpaRatio", recordNumber, nodeKind);
                requireField(unit.getIntermediateStorage(), "intermediateStorage", recordNumber, nodeKind);
                requireField(unit.getVesselDepth(), "vesselDepth", recordNumber, nodeKind);
                requireField(unit.getHiringCost(), "hiringCost", recordNumber, nodeKind);
                requireField(unit.getShipCapacity(), "shipCapacity", recordNumber, nodeKind);
                requireField(unit.getGasConsumption(), "gasConsumption", recordNumber, nodeKind);
            }
            case LNGCarrierDTO carrier -> {
                requireField(carrier.getShipCapacity(), "shipCapacity", recordNumber, nodeKind);
                requireField(carrier.getFullLoadTime(), "fullLoadTime", recordNumber, nodeKind);
                requireField(carrier.getHiringCost(), "hiringCost", recordNumber, nodeKind);
            }
            case SeaportTerminalDTO terminal -> {
                requireField(terminal.getIntermediateStorage(), "intermediateStorage", recordNumber, nodeKind);
                requireField(terminal.getPortDepth(), "portDepth", recordNumber, nodeKind);
            }
            case InternalConsumptionDTO consumption -> {
                requireField(consumption.getConsumptionSummer(), "consumptionSummer", recordNumber, nodeKind);
                requireField(consumption.getConsumptionAutumn(), "consumptionAutumn", recordNumber, nodeKind);
                requireField(consumption.getConsumptionWinter(), "consumptionWinter", recordNumber, nodeKind);
                requireField(consumption.getConsumptionSpring(), "consumptionSpring", recordNumber, nodeKind);
            }
            case IndustrialConsumptionDTO consumption ->
                    requireField(consumption.getConsumption(), "consumption", recordNumber, nodeKind);
            default -> {
            }
        }
    }

    private void requireField(Object value, String field, int recordNumber, NodeKind nodeKind) {
        if (value == null) {
            throw invalidRequiredField(recordNumber, nodeKind, field);
        }
    }

    private IllegalArgumentException invalidRequiredField(int recordNumber, NodeKind nodeKind, String field) {
        return new IllegalArgumentException(
                "CSV record %d (%s) is missing required field '%s'".formatted(recordNumber, nodeKind, field));
    }

    private void validateTypeConsistency(BaseNodeDTO node, NodeKind nodeKind, int recordNumber) {
        if (node.getType().getNodeType() != nodeKind.expectedNodeType
                || node.getType().getVertical() != nodeKind.expectedVertical) {
            throw new IllegalArgumentException(
                    "CSV record %d (%s) has inconsistent node type metadata".formatted(recordNumber, nodeKind));
        }
        if (node instanceof WellDTO well) {
            String distributionType = well.getDeclineCurve().getType();
            if (distributionType == null) {
                throw invalidRequiredField(recordNumber, nodeKind, "declineCurve.type");
            }
        }
    }

    private enum NodeKind {
        WELL(WellDTO.class, NodeTypeEnum.WELL, VerticalEnum.EXTRACTION,
                List.of("maxCollectionCapacity", "declineCurve", "gasRichness", "dtmTime", "dtmCost", "surface")),
        GATHERING_NETWORK(GatheringNetworkDTO.class, NodeTypeEnum.GATHERING_NETWORK, VerticalEnum.EXTRACTION,
                List.of("maxTransportCapacity", "length", "lossPerMeter", "connectedWells")),
        TREATMENT_PLANT(TreatmentPlantDTO.class, NodeTypeEnum.TREATMENT_PLANT, VerticalEnum.EXTRACTION,
                List.of("maxTreatmentCapacity", "contaminantWaste", "intermediateStorage", "treatmentCost")),
        PIPELINE(PipelineDTO.class, NodeTypeEnum.PIPELINE, VerticalEnum.TRANSPORTATION,
                List.of("maxFlowCapacity", "length", "lossPerKm")),
        PIPELINE_CONNECTION(PipelineConnectionDTO.class, NodeTypeEnum.PIPELINE_CONECTION, VerticalEnum.TRANSPORTATION,
                List.of("transferCapacity", "outputPriority")),
        COMPRESSING_PLANT(CompressingPlantDTO.class, NodeTypeEnum.COMPRESSING_PLANT, VerticalEnum.TRANSPORTATION,
                List.of("maxCompressionCapacity", "processWaste", "gasConsumption")),
        GROUND_BASED_LIQUEFACTION_PLANT(GroundBasedLiquefactionPlantDTO.class,
                NodeTypeEnum.GROUND_LIQUEFACTION_PLANT, VerticalEnum.LIQUEFACTION,
                List.of("maxProcessingCapacity", "mtpaRatio", "intermediateStorage", "gasConsumption")),
        FLNG_UNIT(FLNGUnitDTO.class, NodeTypeEnum.FLNG_UNIT, VerticalEnum.LIQUEFACTION,
                List.of("maxProcessingCapacity", "mtpaRatio", "intermediateStorage", "vesselDepth",
                        "hiringCost", "shipCapacity", "gasConsumption")),
        LNG_CARRIER(LNGCarrierDTO.class, NodeTypeEnum.LNG_CAMER, VerticalEnum.EXPORT,
                List.of("exportFrequency", "shipCapacity", "fullLoadTime", "hiringCost", "timeToDestination")),
        SEAPORT_TERMINAL(SeaportTerminalDTO.class, NodeTypeEnum.SEAPORT_TERMINAL, VerticalEnum.EXPORT,
                List.of("intermediateStorage", "portDepth", "shipCapacity")),
        INTERNAL_CONSUMPTION(InternalConsumptionDTO.class, NodeTypeEnum.INTERNAL_CONSUMPTION,
                VerticalEnum.INTERNAL_CONSUMPTION,
                List.of("consumptionSummer", "consumptionAutumn", "consumptionWinter", "consumptionSpring")),
        INDUSTRIAL_CONSUMPTION(IndustrialConsumptionDTO.class, NodeTypeEnum.INDUSTRIAL_CONSUMPTION,
                VerticalEnum.EXPORT, List.of("consumption"));

        private final Class<? extends BaseNodeDTO> dtoClass;
        private final NodeTypeEnum expectedNodeType;
        private final VerticalEnum expectedVertical;
        private final List<String> requiredFields;

        NodeKind(
                Class<? extends BaseNodeDTO> dtoClass,
                NodeTypeEnum expectedNodeType,
                VerticalEnum expectedVertical,
                List<String> requiredFields) {
            this.dtoClass = dtoClass;
            this.expectedNodeType = expectedNodeType;
            this.expectedVertical = expectedVertical;
            this.requiredFields = requiredFields;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
    private abstract static class ConcreteNodeDtoMixin {
    }
}
