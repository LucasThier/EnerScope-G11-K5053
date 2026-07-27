package org.enerscope.node.service;

import org.enerscope.node.dto.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NodeServiceImplTest {

    @Mock
    private WellDTO mockWellDTO;

    @Mock
    private PipelineDTO mockPipelineDTO;

    @Mock
    private GatheringNetworkDTO mockGnDTO;

    @Mock
    private TreatmentPlantDTO mockTpDTO;

    @Mock
    private CompressingPlantDTO mockCpDTO;

    @Mock
    private GroundBasedLiquefactionPlantDTO mockGblpDTO;

    @Mock
    private FLNGUnitDTO mockFlngDTO;

    @Mock
    private LNGCarrierDTO mockLngDTO;

    @Mock
    private SeaportTerminalDTO mockStDTO;

    @Mock
    private PipelineConnectionDTO mockPcDTO;

    @Mock
    private ConnectionDTO mockConnDTO;

    @InjectMocks
    private NodeServiceImpl nodeService;

    @BeforeEach
    void setUp() {
        // Setup mock return values for getters
        when(mockWellDTO.getName()).thenReturn("Test Well");
        when(mockPipelineDTO.getName()).thenReturn("Test Pipeline");
        when(mockGnDTO.getName()).thenReturn("Test Gathering Network");
        when(mockTpDTO.getName()).thenReturn("Test Treatment Plant");
        when(mockCpDTO.getName()).thenReturn("Test Compressing Plant");
        when(mockGblpDTO.getName()).thenReturn("Test GBLP");
        when(mockFlngDTO.getName()).thenReturn("Test FLNG");
        when(mockLngDTO.getName()).thenReturn("Test LNG Carrier");
        when(mockStDTO.getName()).thenReturn("Test Seaport Terminal");
        when(mockPcDTO.getName()).thenReturn("Test Pipeline Connection");

        java.util.UUID uuid1 = java.util.UUID.randomUUID();
        java.util.UUID uuid2 = java.util.UUID.randomUUID();
        when(mockConnDTO.getFromNodeId()).thenReturn(uuid1);
        when(mockConnDTO.getToNodeId()).thenReturn(uuid2);
    }

    @Test
    void createWellShouldReturnWellDTO() {
        // Act
        WellDTO result = nodeService.createWell(mockWellDTO);

        // Assert
        assertSame(mockWellDTO, result);
        assertEquals("Test Well", result.getName());
    }

    @Test
    void createPipelineShouldReturnPipelineDTO() {
        // Act
        PipelineDTO result = nodeService.createPipeline(mockPipelineDTO);

        // Assert
        assertSame(mockPipelineDTO, result);
        assertEquals("Test Pipeline", result.getName());
    }

    @Test
    void createGatheringNetworkShouldReturnGatheringNetworkDTO() {
        // Act
        GatheringNetworkDTO result = nodeService.createGatheringNetwork(mockGnDTO);

        // Assert
        assertSame(mockGnDTO, result);
        assertEquals("Test Gathering Network", result.getName());
    }

    @Test
    void createTreatmentPlantShouldReturnTreatmentPlantDTO() {
        // Act
        TreatmentPlantDTO result = nodeService.createTreatmentPlant(mockTpDTO);

        // Assert
        assertSame(mockTpDTO, result);
        assertEquals("Test Treatment Plant", result.getName());
    }

    @Test
    void createCompressingPlantShouldReturnCompressingPlantDTO() {
        // Act
        CompressingPlantDTO result = nodeService.createCompressingPlant(mockCpDTO);

        // Assert
        assertSame(mockCpDTO, result);
        assertEquals("Test Compressing Plant", result.getName());
    }

    @Test
    void createGroundBasedLiquefactionPlantShouldReturnGroundBasedLiquefactionPlantDTO() {
        // Act
        GroundBasedLiquefactionPlantDTO result = nodeService.createGroundBasedLiquefactionPlant(mockGblpDTO);

        // Assert
        assertSame(mockGblpDTO, result);
        assertEquals("Test GBLP", result.getName());
    }

    @Test
    void createFLNGUnitShouldReturnFLNGUnitDTO() {
        // Act
        FLNGUnitDTO result = nodeService.createFLNGUnit(mockFlngDTO);

        // Assert
        assertSame(mockFlngDTO, result);
        assertEquals("Test FLNG", result.getName());
    }

    @Test
    void createLNGCarrierShouldReturnLNGCarrierDTO() {
        // Act
        LNGCarrierDTO result = nodeService.createLNGCarrier(mockLngDTO);

        // Assert
        assertSame(mockLngDTO, result);
        assertEquals("Test LNG Carrier", result.getName());
    }

    @Test
    void createSeaportTerminalShouldReturnSeaportTerminalDTO() {
        // Act
        SeaportTerminalDTO result = nodeService.createSeaportTerminal(mockStDTO);

        // Assert
        assertSame(mockStDTO, result);
        assertEquals("Test Seaport Terminal", result.getName());
    }

    @Test
    void createPipelineConnectionShouldReturnPipelineConnectionDTO() {
        // Act
        PipelineConnectionDTO result = nodeService.createPipelineConnection(mockPcDTO);

        // Assert
        assertSame(mockPcDTO, result);
        assertEquals("Test Pipeline Connection", result.getName());
    }

    @Test
    void createConnectionShouldReturnConnectionDTO() {
        // Act
        ConnectionDTO result = nodeService.createConnection(mockConnDTO);

        // Assert
        assertSame(mockConnDTO, result);
    }
}