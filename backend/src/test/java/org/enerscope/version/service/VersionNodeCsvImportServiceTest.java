package org.enerscope.version.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.common.CsvUtil;
import org.enerscope.common.ForbiddenException;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.CompressingPlantDTO;
import org.enerscope.node.dto.FLNGUnitDTO;
import org.enerscope.node.dto.GatheringNetworkDTO;
import org.enerscope.node.dto.GroundBasedLiquefactionPlantDTO;
import org.enerscope.node.dto.IndustrialConsumptionDTO;
import org.enerscope.node.dto.InternalConsumptionDTO;
import org.enerscope.node.dto.LNGCarrierDTO;
import org.enerscope.node.dto.PipelineConnectionDTO;
import org.enerscope.node.dto.PipelineDTO;
import org.enerscope.node.dto.SeaportTerminalDTO;
import org.enerscope.node.dto.TreatmentPlantDTO;
import org.enerscope.node.dto.WellDTO;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.service.ProjectAccessGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VersionNodeCsvImportServiceTest {

    @Mock
    private VersionService versionService;

    @Mock
    private ProjectAccessGuard accessGuard;

    @Mock
    private AppLogger logger;

    private VersionNodeCsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService = new VersionNodeCsvImportService(versionService, accessGuard, new ObjectMapper(), logger);
    }

    @ParameterizedTest
    @MethodSource("allNodeKinds")
    void importsEveryNodeKindIntoVersion(String nodeKind, Class<? extends BaseNodeDTO> expectedDto) {
        UUID versionId = UUID.randomUUID();

        int count = importCsv(versionId, validRow(nodeKind));

        assertEquals(1, count);
        ArgumentCaptor<BaseNodeDTO> dtoCaptor = ArgumentCaptor.forClass(BaseNodeDTO.class);
        verify(versionService).addNodeToVersion(eq(versionId), dtoCaptor.capture());
        assertInstanceOf(expectedDto, dtoCaptor.getValue());
        assertEquals("Sample", dtoCaptor.getValue().getName());
    }

    private static Stream<Arguments> allNodeKinds() {
        return Stream.of(
                Arguments.of("WELL", WellDTO.class),
                Arguments.of("GATHERING_NETWORK", GatheringNetworkDTO.class),
                Arguments.of("TREATMENT_PLANT", TreatmentPlantDTO.class),
                Arguments.of("PIPELINE", PipelineDTO.class),
                Arguments.of("PIPELINE_CONNECTION", PipelineConnectionDTO.class),
                Arguments.of("COMPRESSING_PLANT", CompressingPlantDTO.class),
                Arguments.of("GROUND_BASED_LIQUEFACTION_PLANT", GroundBasedLiquefactionPlantDTO.class),
                Arguments.of("FLNG_UNIT", FLNGUnitDTO.class),
                Arguments.of("LNG_CARRIER", LNGCarrierDTO.class),
                Arguments.of("SEAPORT_TERMINAL", SeaportTerminalDTO.class),
                Arguments.of("INTERNAL_CONSUMPTION", InternalConsumptionDTO.class),
                Arguments.of("INDUSTRIAL_CONSUMPTION", IndustrialConsumptionDTO.class));
    }

    private String validRow(String nodeKind) {
        List<String> headers = new java.util.ArrayList<>(
                List.of("nodeKind", "name", "state", "lifespanInMonths", "upkeepCosts",
                        "maintenanceIntervalInDays", "operatingCosts", "wastePercentage", "type", "investmentCost"));
        List<String> values = new java.util.ArrayList<>(List.of(
                nodeKind,
                "Sample",
                "RUNNING",
                "240",
                "10",
                "30",
                "20",
                "0.1",
                "{\"vertical\":\"" + expectedVertical(nodeKind)
                        + "\",\"role\":\"GENERATOR\",\"nodeType\":\""
                        + enumNodeType(nodeKind) + "\"}",
                "{\"components\":[]}"));
        for (String field : requiredTypeFields(nodeKind)) {
            headers.add(field);
            values.add("declineCurve".equals(field)
                    ? "{\"type\":\"CONSTANT\",\"val_a\":0.25}"
                    : "1");
        }
        return CsvUtil.write(List.of(headers, values));
    }

    private String expectedVertical(String nodeKind) {
        return switch (nodeKind) {
            case "GATHERING_NETWORK", "TREATMENT_PLANT", "WELL" -> "EXTRACTION";
            case "PIPELINE", "PIPELINE_CONNECTION", "COMPRESSING_PLANT" -> "TRANSPORTATION";
            case "GROUND_BASED_LIQUEFACTION_PLANT", "FLNG_UNIT" -> "LIQUEFACTION";
            case "INTERNAL_CONSUMPTION" -> "INTERNAL_CONSUMPTION";
            default -> "EXPORT";
        };
    }

    private List<String> requiredTypeFields(String nodeKind) {
        return switch (nodeKind) {
            case "WELL" -> List.of(
                    "maxCollectionCapacity", "declineCurve", "gasRichness", "dtmTime", "dtmCost", "surface");
            case "GATHERING_NETWORK" ->
                    List.of("maxTransportCapacity", "length", "lossPerMeter", "connectedWells");
            case "TREATMENT_PLANT" ->
                    List.of("maxTreatmentCapacity", "contaminantWaste", "intermediateStorage", "treatmentCost");
            case "PIPELINE" -> List.of("maxFlowCapacity", "length", "lossPerKm");
            case "PIPELINE_CONNECTION" -> List.of("transferCapacity", "outputPriority");
            case "COMPRESSING_PLANT" -> List.of("maxCompressionCapacity", "processWaste", "gasConsumption");
            case "GROUND_BASED_LIQUEFACTION_PLANT" ->
                    List.of("maxProcessingCapacity", "mtpaRatio", "intermediateStorage", "gasConsumption");
            case "FLNG_UNIT" -> List.of(
                    "maxProcessingCapacity", "mtpaRatio", "intermediateStorage", "vesselDepth",
                    "hiringCost", "shipCapacity", "gasConsumption");
            case "LNG_CARRIER" ->
                    List.of("exportFrequency", "shipCapacity", "fullLoadTime", "hiringCost", "timeToDestination");
            case "SEAPORT_TERMINAL" -> List.of("intermediateStorage", "portDepth", "shipCapacity");
            case "INTERNAL_CONSUMPTION" ->
                    List.of("consumptionSummer", "consumptionAutumn", "consumptionWinter", "consumptionSpring");
            case "INDUSTRIAL_CONSUMPTION" -> List.of("consumption");
            default -> List.of();
        };
    }

    private String enumNodeType(String nodeKind) {
        return switch (nodeKind) {
            case "PIPELINE_CONNECTION" -> "PIPELINE_CONECTION";
            case "GROUND_BASED_LIQUEFACTION_PLANT" -> "GROUND_LIQUEFACTION_PLANT";
            case "LNG_CARRIER" -> "LNG_CAMER";
            default -> nodeKind;
        };
    }

    @Test
    void importsMixedKindsAndIgnoresEmptyColumnsForOtherKinds() {
        UUID versionId = UUID.randomUUID();
        String csv = CsvUtil.write(List.of(
                List.of("nodeKind", "name", "state", "lifespanInMonths", "upkeepCosts",
                        "maintenanceIntervalInDays", "operatingCosts", "wastePercentage", "type",
                        "investmentCost", "maxFlowCapacity", "length", "lossPerKm", "consumption"),
                List.of("PIPELINE", "Export line", "RUNNING", "240", "10", "30", "20", "0.1",
                        "{\"vertical\":\"TRANSPORTATION\",\"role\":\"INTERMEDIATE\",\"nodeType\":\"PIPELINE\"}",
                        "{\"components\":[]}", "120", "10", "0.1", ""),
                List.of("INDUSTRIAL_CONSUMPTION", "Factory", "RUNNING", "240", "10", "30", "20", "0.1",
                        "{\"vertical\":\"EXPORT\",\"role\":\"SINK\",\"nodeType\":\"INDUSTRIAL_CONSUMPTION\"}",
                        "{\"components\":[]}", "", "", "", "75")));

        int count = importCsv(versionId, csv);

        assertEquals(2, count);
        verify(versionService).addNodeToVersion(eq(versionId), any(PipelineDTO.class));
        verify(versionService).addNodeToVersion(eq(versionId), any(IndustrialConsumptionDTO.class));
    }

    @Test
    void mapsNestedJsonDtoFields() {
        UUID versionId = UUID.randomUUID();
        String csv = CsvUtil.write(List.of(
                List.of("nodeKind", "name", "state", "lifespanInMonths", "upkeepCosts",
                        "maintenanceIntervalInDays", "operatingCosts", "wastePercentage",
                        "graphData", "type", "investmentCost", "declineCurve", "dtmCost",
                        "maxCollectionCapacity", "gasRichness", "dtmTime", "surface"),
                List.of("WELL", "North well", "RUNNING", "240", "10", "30", "20", "0.1",
                        "{\"graphPosition\":{\"x\":12.0,\"y\":34.0}}",
                        "{\"vertical\":\"EXTRACTION\",\"role\":\"GENERATOR\",\"nodeType\":\"WELL\"}",
                        "{\"components\":[]}",
                        "{\"type\":\"CONSTANT\",\"val_a\":0.25}", "1", "100", "0.2", "1", "50")));

        importCsv(versionId, csv);

        ArgumentCaptor<BaseNodeDTO> dtoCaptor = ArgumentCaptor.forClass(BaseNodeDTO.class);
        verify(versionService).addNodeToVersion(eq(versionId), dtoCaptor.capture());
        WellDTO well = assertInstanceOf(WellDTO.class, dtoCaptor.getValue());
        assertEquals(12.0, well.getGraphData().getGraphPosition().getX());
        assertEquals("CONSTANT", well.getDeclineCurve().getType());
    }

    @Test
    void rejectsUnknownNodeKindWithSupportedKinds() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), "nodeKind,name\nUNKNOWN,Sample"));

        assertTrue(exception.getMessage().contains("Unsupported nodeKind 'UNKNOWN'"), exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsUnknownHeaders() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), "nodeKind,nmae\nWELL,Sample"));

        assertTrue(exception.getMessage().contains("Unknown CSV header(s): nmae"), exception.getMessage());
    }

    @Test
    void rejectsRowsWithDifferentColumnCounts() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), "nodeKind,name\nWELL,Sample,Extra"));

        assertTrue(exception.getMessage().contains("CSV record 2 has 3 fields; expected 2"), exception.getMessage());
    }

    @Test
    void rejectsValuesInColumnsNotSupportedByTheSelectedKind() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).add("maxFlowCapacity");
        rows.get(1).add("10");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), CsvUtil.write(rows)));

        assertTrue(exception.getMessage().contains("unsupported column 'maxFlowCapacity'"), exception.getMessage());
    }

    @Test
    void rejectsRowsMissingRequiredFields() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), "nodeKind,name\nWELL,Sample"));

        assertTrue(exception.getMessage().contains("missing required field 'state'"), exception.getMessage());
    }

    @Test
    void rejectsMalformedNestedJson() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), withCell(validRow("WELL"), "graphData", "{invalid}")));

        assertTrue(exception.getMessage().contains("invalid JSON in column 'graphData'"), exception.getMessage());
    }

    @Test
    void checksEditPermissionBeforeParsingCsv() {
        UUID versionId = UUID.randomUUID();
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(accessGuard).assertCanEditVersion(versionId);

        ForbiddenException exception = assertThrows(ForbiddenException.class,
                () -> importCsv(versionId, "nodeKind,name\n\"unterminated"));

        assertEquals("You are not allowed to edit this project", exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsMalformedCsvQuoting() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), "nodeKind,name\nWELL,\"Sample\"bad"));

        assertTrue(exception.getMessage().contains("Unexpected character after a quoted CSV field"));
    }

    @Test
    void rejectsInconsistentNodeTypeMetadata() {
        String csv = withCell(validRow("WELL"), "type",
                "{\"vertical\":\"EXTRACTION\",\"role\":\"GENERATOR\",\"nodeType\":\"PIPELINE\"}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("expected 'WELL'"), exception.getMessage());
    }

    @Test
    void rejectsMissingRequiredTypeSpecificFields() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(),
                        "nodeKind,name,state,lifespanInMonths,upkeepCosts,maintenanceIntervalInDays,"
                                + "operatingCosts,wastePercentage,type,investmentCost\n"
                                + "GATHERING_NETWORK,Network,RUNNING,120,10,30,20,0.1,"
                                + "\"{\"\"vertical\"\":\"\"EXTRACTION\"\",\"\"role\"\":\"\"INTERMEDIATE\"\","
                                + "\"\"nodeType\"\":\"\"GATHERING_NETWORK\"\"}\",\"{\"\"components\"\":[]}\""));

        assertTrue(exception.getMessage().contains("missing required field 'maxTransportCapacity'"),
                exception.getMessage());
    }

    @Test
    void rejectsInvalidDistributionParameters() {
        String csv = withCell(validRow("WELL"), "declineCurve",
                "{\"type\":\"UNIFORM\",\"val_a\":2,\"val_b\":1}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("uniform val_a must be less than val_b"), exception.getMessage());
    }

    @Test
    void rejectsInvalidUtf8() {
        byte[] invalidUtf8 = {(byte) 0xC3, (byte) 0x28};

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> csvImportService.importNodes(UUID.randomUUID(), invalidUtf8));

        assertTrue(exception.getMessage().contains("valid UTF-8"));
    }

    @Test
    void rejectsImportsOverMaximumRowCount() {
        StringBuilder csv = new StringBuilder("nodeKind,name\n");
        for (int i = 0; i <= VersionNodeCsvImportService.MAX_DATA_ROWS; i++) {
            csv.append("WELL,Sample\n");
        }

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv.toString()));

        assertTrue(exception.getMessage().contains("maximum of 10000 rows"));
    }

    @Test
    void rejectsFilesOverMaximumSize() {
        byte[] oversizedCsv = new byte[(int) VersionNodeCsvImportService.MAX_FILE_SIZE_BYTES + 1];

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> csvImportService.importNodes(UUID.randomUUID(), oversizedCsv));

        assertTrue(exception.getMessage().contains("5 MB or smaller"));
    }

    private int importCsv(UUID versionId, String csv) {
        return csvImportService.importNodes(versionId, csv.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String withCell(String csv, String header, String value) {
        List<List<String>> rows = CsvUtil.parseStrict(csv, 3);
        int column = rows.get(0).indexOf(header);
        if (column < 0) {
            column = rows.get(0).size();
            rows.get(0).add(header);
            rows.get(1).add(value);
        } else {
            rows.get(1).set(column, value);
        }
        return CsvUtil.write(rows);
    }
}
