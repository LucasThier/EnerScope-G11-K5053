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

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NodeControllerTest {

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
    void createWellShouldReturnOk() throws Exception {
        // Arrange
        WellDTO wellDTO = new WellDTO();
        wellDTO.setName("Test Well");
        wellDTO.setMaxCollectionCapacity(1000.0f);
        wellDTO.setDeclineCurve(0.05f);

        when(nodeService.createWell(any(WellDTO.class))).thenReturn(wellDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/well")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wellDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Well");
                    assert response.contains("Well created successfully");
                });
    }

    @Test
    void createPipelineShouldReturnOk() throws Exception {
        // Arrange
        PipelineDTO pipelineDTO = new PipelineDTO();
        pipelineDTO.setName("Test Pipeline");
        pipelineDTO.setMaxFlowCapacity(500.0f);
        pipelineDTO.setLength(10.5f);

        when(nodeService.createPipeline(any(PipelineDTO.class))).thenReturn(pipelineDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/pipeline")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pipelineDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Pipeline");
                    assert response.contains("Pipeline created successfully");
                });
    }

    @Test
    void createConnectionShouldReturnOk() throws Exception {
        // Arrange
        ConnectionDTO connectionDTO = new ConnectionDTO();
        connectionDTO.setFromNodeId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
        connectionDTO.setToNodeId(UUID.fromString("22222222-2222-2222-2222-222222222222"));

        when(nodeService.createConnection(any(ConnectionDTO.class))).thenReturn(connectionDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/connections")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(connectionDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("11111111-1111-1111-1111-111111111111");
                    assert response.contains("22222222-2222-2222-2222-222222222222");
                    assert response.contains("Connection created successfully");
                });
    }

    @Test
    void createFLNGUnitShouldReturnOk() throws Exception {
        // Arrange
        FLNGUnitDTO flngDTO = new FLNGUnitDTO();
        flngDTO.setName("Test FLNG");
        flngDTO.setMaxProcessingCapacity(200.0f);
        flngDTO.setMTPARatio(1.5f);

        when(nodeService.createFLNGUnit(any(FLNGUnitDTO.class))).thenReturn(flngDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/flng-unit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(flngDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test FLNG");
                    assert response.contains("FLNG Unit created successfully");
                });
    }

    @Test
    void createSeaportTerminalShouldReturnOk() throws Exception {
        // Arrange
        SeaportTerminalDTO stDTO = new SeaportTerminalDTO();
        stDTO.setName("Test Seaport");
        stDTO.setIntermediateStorage(5000.0f);
        stDTO.setPortDepth(15.0f);

        when(nodeService.createSeaportTerminal(any(SeaportTerminalDTO.class))).thenReturn(stDTO);

        // Act & Assert
        mockMvc.perform(post("/nodes/seaport-terminal")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(stDTO)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(result -> {
                    String response = result.getResponse().getContentAsString();
                    assert response.contains("Test Seaport");
                    assert response.contains("Seaport Terminal created successfully");
                });
    }
}