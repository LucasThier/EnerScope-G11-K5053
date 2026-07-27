package org.enerscope.node.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.node.dto.*;
import org.enerscope.node.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdditionalNodeControllerTest {

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @Mock
    private NodeService nodeService;

    @InjectMocks
    private NodeController nodeController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(nodeController).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    void createCompressingPlantShouldReturnOk() throws Exception {
        // Arrange
        CompressingPlantDTO cpDTO = new CompressingPlantDTO();
        cpDTO.setName("Test Compressor");
        cpDTO.setMaxCompressionCapacity(150.0f);
        cpDTO.setProcessWaste(0.02f);

        when(nodeService.createCompressingPlant(any(CompressingPlantDTO.class))).thenReturn(cpDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/compressing-plant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cpDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Compressor");
                    assert response.contains("Compressing Plant created successfully");
                });
    }

    @Test
    void createGroundBasedLiquefactionPlantShouldReturnOk() throws Exception {
        // Arrange
        GroundBasedLiquefactionPlantDTO gblpDTO = new GroundBasedLiquefactionPlantDTO();
        gblpDTO.setName("Test GBLP");
        gblpDTO.setMaxProcessingCapacity(75.0f);
        gblpDTO.setMTPARatio(0.8f);

        when(nodeService.createGroundBasedLiquefactionPlant(any(GroundBasedLiquefactionPlantDTO.class))).thenReturn(gblpDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/ground-liquefaction-plant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gblpDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test GBLP");
                    assert response.contains("Ground Based Liquefaction Plant created successfully");
                });
    }

    @Test
    void createTreatmentPlantShouldReturnOk() throws Exception {
        // Arrange
        TreatmentPlantDTO tpDTO = new TreatmentPlantDTO();
        tpDTO.setName("Test Treatment Plant");
        tpDTO.setWastePercentage(2.0f);

        when(nodeService.createTreatmentPlant(any(TreatmentPlantDTO.class))).thenReturn(tpDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/treatment-plant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tpDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Treatment Plant");
                    assert response.contains("Treatment Plant created successfully");
                });
    }

    @Test
    void createGatheringNetworkShouldReturnOk() throws Exception {
        // Arrange
        GatheringNetworkDTO gnDTO = new GatheringNetworkDTO();
        gnDTO.setName("Test Gathering Network");
        gnDTO.setLifespanInMonths(200);

        when(nodeService.createGatheringNetwork(any(GatheringNetworkDTO.class))).thenReturn(gnDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/gathering-network")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(gnDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Gathering Network");
                    assert response.contains("Gathering Network created successfully");
                });
    }

    @Test
    void createPipelineConnectionShouldReturnOk() throws Exception {
        // Arrange
        PipelineConnectionDTO pcDTO = new PipelineConnectionDTO();
        pcDTO.setName("Test Pipeline Connection");
        pcDTO.setTransferCapacity(300.0f);
        pcDTO.setOutputPriority(1.5f);

        when(nodeService.createPipelineConnection(any(PipelineConnectionDTO.class))).thenReturn(pcDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/pipeline-connection")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pcDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Pipeline Connection");
                    assert response.contains("Pipeline Connection created successfully");
                });
    }
}