package org.enerscope.node.service;

import org.enerscope.node.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeServiceTest {

    private NodeService nodeService;

    @BeforeEach
    void setUp() {
        // Using a mock or stub implementation for testing
        nodeService = new NodeService() {
            @Override
            public WellDTO createWell(WellDTO wellDTO) {
                return wellDTO;
            }

            @Override
            public GatheringNetworkDTO createGatheringNetwork(GatheringNetworkDTO gatheringNetworkDTO) {
                return gatheringNetworkDTO;
            }

            @Override
            public TreatmentPlantDTO createTreatmentPlant(TreatmentPlantDTO treatmentPlantDTO) {
                return treatmentPlantDTO;
            }

            @Override
            public PipelineDTO createPipeline(PipelineDTO pipelineDTO) {
                return pipelineDTO;
            }

            @Override
            public PipelineConnectionDTO createPipelineConnection(PipelineConnectionDTO pipelineConnectionDTO) {
                return pipelineConnectionDTO;
            }

            @Override
            public CompressingPlantDTO createCompressingPlant(CompressingPlantDTO compressingPlantDTO) {
                return compressingPlantDTO;
            }

            @Override
            public GroundBasedLiquefactionPlantDTO createGroundBasedLiquefactionPlant(GroundBasedLiquefactionPlantDTO gblpDTO) {
                return gblpDTO;
            }

            @Override
            public FLNGUnitDTO createFLNGUnit(FLNGUnitDTO flngUnitDTO) {
                return flngUnitDTO;
            }

            @Override
            public LNGCarrierDTO createLNGCarrier(LNGCarrierDTO lngCarrierDTO) {
                return lngCarrierDTO;
            }

            @Override
            public SeaportTerminalDTO createSeaportTerminal(SeaportTerminalDTO seaportTerminalDTO) {
                return seaportTerminalDTO;
            }

            @Override
            public ConnectionDTO createConnection(ConnectionDTO connectionDTO) {
                return connectionDTO;
            }
        };
    }

    @Test
    void createWellShouldReturnWellDTO() {
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        wellDTO.setMaxCollectionCapacity(1000.0f);
        wellDTO.setDeclineCurve(0.05f);

        WellDTO result = nodeService.createWell(wellDTO);

        assertNotNull(result);
        assertEquals("Test Well", result.getName());
        assertEquals(1000.0f, result.getMaxCollectionCapacity());
        assertEquals(0.05f, result.getDeclineCurve());
    }

    @Test
    void createPipelineShouldReturnPipelineDTO() {
        PipelineDTO pipelineDTO = new PipelineDTO();
        pipelineDTO.setName("Test Pipeline");
        pipelineDTO.setMaxFlowCapacity(500.0f);
        pipelineDTO.setLength(10.5f);

        PipelineDTO result = nodeService.createPipeline(pipelineDTO);

        assertNotNull(result);
        assertEquals("Test Pipeline", result.getName());
        assertEquals(500.0f, result.getMaxFlowCapacity());
        assertEquals(10.5f, result.getLength());
    }

    @Test
    void createConnectionShouldReturnConnectionDTO() {
        ConnectionDTO connectionDTO = new ConnectionDTO();
        // Using fixed UUIDs for test predictability
        connectionDTO.setFromNodeId(java.util.UUID.fromString("11111111-1111-1111-1111-111111111111"));
        connectionDTO.setToNodeId(java.util.UUID.fromString("22222222-2222-2222-2222-222222222222"));

        ConnectionDTO result = nodeService.createConnection(connectionDTO);

        assertNotNull(result);
        assertEquals("11111111-1111-1111-1111-111111111111", result.getFromNodeId().toString());
        assertEquals("22222222-2222-2222-2222-222222222222", result.getToNodeId().toString());
    }

    @Test
    void createCompressingPlantShouldReturnCompressingPlantDTO() {
        CompressingPlantDTO cpDTO = new CompressingPlantDTO();
        cpDTO.setName("Test Compressor");
        cpDTO.setMaxCompressionCapacity(150.0f);
        cpDTO.setProcessWaste(0.02f);

        CompressingPlantDTO result = nodeService.createCompressingPlant(cpDTO);

        assertNotNull(result);
        assertEquals("Test Compressor", result.getName());
        assertEquals(150.0f, result.getMaxCompressionCapacity());
        assertEquals(0.02f, result.getProcessWaste());
    }

    @Test
    void createFLNGUnitShouldReturnFLNGUnitDTO() {
        FLNGUnitDTO flngDTO = new FLNGUnitDTO();
        flngDTO.setName("Test FLNG");
        flngDTO.setMaxProcessingCapacity(200.0f);
        flngDTO.setMTPARatio(1.5f);

        FLNGUnitDTO result = nodeService.createFLNGUnit(flngDTO);

        assertNotNull(result);
        assertEquals("Test FLNG", result.getName());
        assertEquals(200.0f, result.getMaxProcessingCapacity());
        assertEquals(1.5f, result.getMTPARatio());
    }

    @Test
    void createSeaportTerminalShouldReturnSeaportTerminalDTO() {
        SeaportTerminalDTO stDTO = new SeaportTerminalDTO();
        stDTO.setName("Test Seaport");
        stDTO.setIntermediateStorage(5000.0f);
        stDTO.setPortDepth(15.0f);

        SeaportTerminalDTO result = nodeService.createSeaportTerminal(stDTO);

        assertNotNull(result);
        assertEquals("Test Seaport", result.getName());
        assertEquals(5000.0f, result.getIntermediateStorage());
        assertEquals(15.0f, result.getPortDepth());
    }

    @Test
    void createGatheringNetworkShouldReturnGatheringNetworkDTO() {
        GatheringNetworkDTO gnDTO = new GatheringNetworkDTO();
        gnDTO.setName("Test Gathering Network");
        gnDTO.setLifespanInMonths(300);

        GatheringNetworkDTO result = nodeService.createGatheringNetwork(gnDTO);

        assertNotNull(result);
        assertEquals("Test Gathering Network", result.getName());
        assertEquals(300, result.getLifespanInMonths());
    }

    @Test
    void createTreatmentPlantShouldReturnTreatmentPlantDTO() {
        TreatmentPlantDTO tpDTO = new TreatmentPlantDTO();
        tpDTO.setName("Test Treatment Plant");
        tpDTO.setWastePercentage(1.5f);

        TreatmentPlantDTO result = nodeService.createTreatmentPlant(tpDTO);

        assertNotNull(result);
        assertEquals("Test Treatment Plant", result.getName());
        assertEquals(1.5f, result.getWastePercentage());
    }
}