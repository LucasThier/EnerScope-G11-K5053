package org.enerscope.version.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.common.CsvUtil;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.common.ForbiddenException;
import org.enerscope.economic.model.configuration.EconomicConfiguration;
import org.enerscope.economic.service.EconomicService;
import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.CompressingPlantDTO;
import org.enerscope.node.dto.ConnectionDTO;
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
import org.enerscope.node.model.BaseNode;
import org.enerscope.logging.AppLogger;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class VersionNodeCsvImportServiceTest {

    @Mock
    private VersionService versionService;

    @Mock
    private EconomicService economicService;

    @Mock
    private VersionAccessGuard accessGuard;

    @Mock
    private AppLogger logger;

    private VersionNodeCsvImportService csvImportService;

    @BeforeEach
    void setUp() {
        csvImportService = new VersionNodeCsvImportService(
                versionService, economicService, accessGuard, new ObjectMapper().findAndRegisterModules(), logger);
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
            case "INTERNAL_CONSUMPTION", "INDUSTRIAL_CONSUMPTION" -> "INTERNAL_CONSUMPTION";
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
                        "{\"vertical\":\"INTERNAL_CONSUMPTION\",\"role\":\"SINK\",\"nodeType\":\"INDUSTRIAL_CONSUMPTION\"}",
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
    void importsNodeAndScenarioEconomicsAndResolvesNodeReferences() {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        BaseNode savedNode = mock(BaseNode.class);
        when(savedNode.getId()).thenReturn(nodeId);
        when(versionService.addNodeToVersion(eq(versionId), any(WellDTO.class))).thenReturn(savedNode);

        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("id", "recordType", "economicProfile", "economicScenario"));
        rows.get(1).addAll(List.of(
                "well-north",
                "NODE",
                "{\"ownership\":[{\"taxEntityId\":\"entity-1\",\"share\":1}],\"rules\":[]}",
                ""));
        List<String> scenarioRow = new java.util.ArrayList<>(
                java.util.Collections.nCopies(rows.get(0).size(), ""));
        scenarioRow.set(rows.get(0).indexOf("recordType"), "SCENARIO");
        scenarioRow.set(rows.get(0).indexOf("economicScenario"), """
                {"startYear":2025,"years":1,"currency":"USD","wacc":0.1,
                 "taxEntities":[],"boundary":[],"adjustments":[],"contracts":[],
                 "assets":[{"id":"asset-1","nodeId":"well-north","taxEntityId":"entity-1",
                   "concept":"equipment","currency":"USD","cost":100,"residualValue":0,
                   "purchaseDate":"2025-01-01","paymentDate":"2025-01-01",
                   "serviceDate":"2025-01-01","usefulLifeMonths":12}],
                 "taxTreatments":[],
                 "conversions":[{"nodeId":"well-north","metric":"INPUT_VOLUME",
                   "rawUnit":"SIMULATOR_UNIT","unit":"Mcf","factor":1}],
                 "contracts":[{"id":"contract-1","deliveryNodeId":"well-north","revenueRule":{}}]}
                """.replace("\n", ""));
        rows.add(scenarioRow);

        int importedCount = importCsv(versionId, CsvUtil.write(rows));

        assertEquals(1, importedCount);
        ArgumentCaptor<EconomicConfiguration> configurationCaptor =
                ArgumentCaptor.forClass(EconomicConfiguration.class);
        verify(economicService).saveImportedConfiguration(eq(versionId), configurationCaptor.capture());
        EconomicConfiguration configuration = configurationCaptor.getValue();
        assertEquals(2025, configuration.startYear());
        assertEquals(nodeId, configuration.nodeProfiles().getFirst().nodeId());
        assertEquals("entity-1", configuration.nodeProfiles().getFirst().ownership().getFirst().taxEntityId());
        assertEquals(nodeId, configuration.conversions().getFirst().nodeId());
        assertEquals(nodeId, configuration.assets().getFirst().nodeId());
        assertEquals(nodeId, configuration.contracts().getFirst().deliveryNodeId());
    }

    @Test
    void rejectsEconomicReferencesToUnknownExternalNodeIdsBeforePersisting() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("id", "recordType", "economicScenario"));
        rows.get(1).addAll(List.of("well-north", "NODE", ""));
        List<String> scenarioRow = new java.util.ArrayList<>(
                java.util.Collections.nCopies(rows.get(0).size(), ""));
        scenarioRow.set(rows.get(0).indexOf("recordType"), "SCENARIO");
        scenarioRow.set(rows.get(0).indexOf("economicScenario"), """
                {"startYear":2025,"years":1,"currency":"USD","wacc":0.1,
                 "taxEntities":[],"boundary":[],"adjustments":[],"contracts":[],
                 "assets":[],"taxTreatments":[],
                 "conversions":[{"nodeId":"missing-well","metric":"INPUT_VOLUME",
                   "rawUnit":"SIMULATOR_UNIT","unit":"Mcf","factor":1}]}
                """.replace("\n", ""));
        rows.add(scenarioRow);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), CsvUtil.write(rows)));

        assertTrue(exception.getMessage().contains("references unknown imported node id 'missing-well'"));
        verify(versionService, never()).addNodeToVersion(any(), any());
        verify(economicService, never()).saveImportedConfiguration(any(), any());
    }

    @Test
    void rejectsNodeEconomicProfileWithoutScenarioConfigurationBeforePersisting() {
        String csv = withCell(validRow("WELL"), "economicProfile",
                "{\"ownership\":[],\"rules\":[]}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("require one SCENARIO record"));
        verify(versionService, never()).addNodeToVersion(any(), any());
        verify(economicService, never()).saveImportedConfiguration(any(), any());
    }

    @Test
    void rejectsUnknownRecordTypeBeforePersisting() {
        String csv = withCell(validRow("WELL"), "recordType", "UNKNOWN");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("Supported values: NODE, CONNECTION, SCENARIO"));
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void importsConnectionsByResolvingProvidedNodeIdsAfterNodeCreation() {
        UUID versionId = UUID.randomUUID();
        UUID fromNodeId = UUID.randomUUID();
        UUID toNodeId = UUID.randomUUID();
        BaseNode fromNode = mock(BaseNode.class);
        BaseNode toNode = mock(BaseNode.class);
        when(fromNode.getId()).thenReturn(fromNodeId);
        when(toNode.getId()).thenReturn(toNodeId);
        when(versionService.addNodeToVersion(eq(versionId), any(BaseNodeDTO.class)))
                .thenReturn(fromNode, toNode);

        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("id", "recordType", "fromNodeId", "toNodeId"));
        rows.get(1).addAll(List.of("source-well", "NODE", "", ""));
        List<String> secondNodeRow = new java.util.ArrayList<>(rows.get(1));
        secondNodeRow.set(rows.get(0).indexOf("id"), "target-well");
        rows.add(secondNodeRow);
        List<String> connectionRow = new java.util.ArrayList<>(
                java.util.Collections.nCopies(rows.get(0).size(), ""));
        connectionRow.set(rows.get(0).indexOf("recordType"), "CONNECTION");
        connectionRow.set(rows.get(0).indexOf("fromNodeId"), "source-well");
        connectionRow.set(rows.get(0).indexOf("toNodeId"), "target-well");
        rows.add(connectionRow);

        assertEquals(2, importCsv(versionId, CsvUtil.write(rows)));

        ArgumentCaptor<ConnectionDTO> connectionCaptor = ArgumentCaptor.forClass(ConnectionDTO.class);
        verify(versionService).addConnectionToVersion(eq(versionId), connectionCaptor.capture());
        assertEquals(fromNodeId, connectionCaptor.getValue().getFromNodeId());
        assertEquals(toNodeId, connectionCaptor.getValue().getToNodeId());
        assertNull(connectionCaptor.getValue().getIdentity());
        ArgumentCaptor<BaseNodeDTO> nodeCaptor = ArgumentCaptor.forClass(BaseNodeDTO.class);
        verify(versionService, times(2)).addNodeToVersion(eq(versionId), nodeCaptor.capture());
        assertNull(nodeCaptor.getAllValues().getFirst().getIdentity());
    }

    @Test
    void rejectsConnectionReferencesToUnknownNodeIdsBeforePersisting() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("id", "recordType", "fromNodeId", "toNodeId"));
        rows.get(1).addAll(List.of("source-well", "NODE", "", ""));
        List<String> connectionRow = new java.util.ArrayList<>(
                java.util.Collections.nCopies(rows.get(0).size(), ""));
        connectionRow.set(rows.get(0).indexOf("recordType"), "CONNECTION");
        connectionRow.set(rows.get(0).indexOf("fromNodeId"), "source-well");
        connectionRow.set(rows.get(0).indexOf("toNodeId"), "missing-well");
        rows.add(connectionRow);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), CsvUtil.write(rows)));

        assertTrue(exception.getMessage().contains("references unknown node id 'missing-well'"));
        verify(versionService, never()).addNodeToVersion(any(), any());
        verify(versionService, never()).addConnectionToVersion(any(), any());
    }

    @Test
    void rejectsDuplicateExternalNodeIdsBeforePersisting() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("id", "recordType"));
        rows.get(1).addAll(List.of("well-1", "NODE"));
        List<String> duplicateNodeRow = new java.util.ArrayList<>(rows.get(1));
        rows.add(duplicateNodeRow);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), CsvUtil.write(rows)));

        assertTrue(exception.getMessage().contains("duplicate node id 'well-1'"));
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsMalformedEconomicProfileJsonBeforePersisting() {
        String csv = withCell(validRow("WELL"), "economicProfile", "{invalid}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("invalid JSON in column 'economicProfile'"));
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsScenarioRowsContainingNodeDataBeforePersisting() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("recordType", "economicScenario"));
        rows.get(1).addAll(List.of("SCENARIO", "{}"));
        String csv = CsvUtil.write(rows);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertTrue(exception.getMessage().contains("must leave 'nodeKind' blank"));
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsDuplicateScenarioRowsBeforePersisting() {
        List<List<String>> rows = CsvUtil.parseStrict(validRow("WELL"), 3);
        rows.get(0).addAll(List.of("recordType", "economicScenario"));
        rows.get(1).addAll(List.of("NODE", ""));
        List<String> scenarioRow = new java.util.ArrayList<>(
                java.util.Collections.nCopies(rows.get(0).size(), ""));
        scenarioRow.set(rows.get(0).indexOf("recordType"), "SCENARIO");
        scenarioRow.set(rows.get(0).indexOf("economicScenario"), "{}");
        rows.add(scenarioRow);
        rows.add(new java.util.ArrayList<>(scenarioRow));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), CsvUtil.write(rows)));

        assertTrue(exception.getMessage().contains("only one SCENARIO record"));
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void importsIndustrialConsumptionDeclaredInTheVerticalOfNodeTypeEnum() {
        UUID versionId = UUID.randomUUID();
        String csv = withCell(validRow("INDUSTRIAL_CONSUMPTION"), "type",
                "{\"vertical\":\"INTERNAL_CONSUMPTION\",\"role\":\"SINK\",\"nodeType\":\"INDUSTRIAL_CONSUMPTION\"}");

        assertEquals(1, importCsv(versionId, csv));

        ArgumentCaptor<BaseNodeDTO> dtoCaptor = ArgumentCaptor.forClass(BaseNodeDTO.class);
        verify(versionService).addNodeToVersion(eq(versionId), dtoCaptor.capture());
        IndustrialConsumptionDTO node = assertInstanceOf(IndustrialConsumptionDTO.class, dtoCaptor.getValue());
        assertEquals(VerticalEnum.INTERNAL_CONSUMPTION, node.getType().getVertical());
        assertEquals(NodeTypeEnum.INDUSTRIAL_CONSUMPTION.vertical(), node.getType().getVertical());
    }

    @Test
    void rejectsIndustrialConsumptionDeclaredAsExportBeforePersisting() {
        String csv = withCell(validRow("INDUSTRIAL_CONSUMPTION"), "type",
                "{\"vertical\":\"EXPORT\",\"role\":\"SINK\",\"nodeType\":\"INDUSTRIAL_CONSUMPTION\"}");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(UUID.randomUUID(), csv));

        assertEquals("CSV record 2 (INDUSTRIAL_CONSUMPTION) has type.vertical 'EXPORT'; expected 'INTERNAL_CONSUMPTION'",
                exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
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

        assertTrue(exception.getMessage().contains("10 MB or smaller"));
    }

    @Test
    void rejectsIdentityThatAlreadyExistsInTheVersionBeforePersisting() {
        UUID versionId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        when(versionService.nodeIdentities(versionId)).thenReturn(java.util.Set.of(identity));
        String csv = withCell(validRow("WELL"), "identity", identity.toString());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(versionId, csv));

        assertEquals("Invalid CSV record 2 (NODE): identity %s already exists in this version".formatted(identity),
                exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
        verify(versionService, never()).addConnectionToVersion(any(), any());
        verify(economicService, never()).saveImportedConfiguration(any(), any());
    }

    @Test
    void rejectsExistingIdentityOnALaterRowWithoutWritingTheEarlierValidRows() {
        UUID versionId = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        when(versionService.nodeIdentities(versionId)).thenReturn(java.util.Set.of(existing));
        List<List<String>> rows = CsvUtil.parseStrict(withCell(validRow("WELL"), "identity", UUID.randomUUID().toString()), 3);
        List<String> second = new java.util.ArrayList<>(rows.get(1));
        second.set(rows.get(0).indexOf("identity"), existing.toString());
        rows.add(second);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(versionId, CsvUtil.write(rows)));

        assertEquals("Invalid CSV record 3 (NODE): identity %s already exists in this version".formatted(existing),
                exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void rejectsIdentityRepeatedInTheFileBeforePersisting() {
        UUID versionId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        when(versionService.nodeIdentities(versionId)).thenReturn(java.util.Set.of());
        List<List<String>> rows = CsvUtil.parseStrict(withCell(validRow("WELL"), "identity", identity.toString()), 3);
        rows.add(new java.util.ArrayList<>(rows.get(1)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> importCsv(versionId, CsvUtil.write(rows)));

        assertEquals("Invalid CSV record 3 (NODE): identity %s is repeated in the file".formatted(identity),
                exception.getMessage());
        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    @Test
    void importsANewIdentityThatIsNotInTheVersion() {
        UUID versionId = UUID.randomUUID();
        UUID identity = UUID.randomUUID();
        when(versionService.nodeIdentities(versionId)).thenReturn(java.util.Set.of(UUID.randomUUID()));

        int count = importCsv(versionId, withCell(validRow("WELL"), "identity", identity.toString()));

        assertEquals(1, count);
        ArgumentCaptor<BaseNodeDTO> dtoCaptor = ArgumentCaptor.forClass(BaseNodeDTO.class);
        verify(versionService).addNodeToVersion(eq(versionId), dtoCaptor.capture());
        assertEquals(identity, dtoCaptor.getValue().getIdentity());
    }

    @Test
    void aFileWithoutIdentityColumnNeverLooksUpTheVersionIdentities() {
        UUID versionId = UUID.randomUUID();

        assertEquals(1, importCsv(versionId, validRow("WELL")));

        verify(versionService, never()).nodeIdentities(any());
        verify(versionService).addNodeToVersion(eq(versionId), any(WellDTO.class));
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
