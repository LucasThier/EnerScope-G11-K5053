package org.enerscope.node.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.enerscope.money.MoneyAmount;
import org.enerscope.node.dto.*;
import java.util.UUID;
import org.enerscope.node.model.enums.CostBasisEnum;
import org.enerscope.node.model.enums.NodeStateEnum;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.node.repository.WellRepository;
import org.enerscope.node.service.NodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class NodeControllerTest {

    UUID id = UUID.randomUUID();

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Mock
    private WellRepository wellRepository;

    @Bean
    @Autowired
    public ObjectMapper defaultMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return objectMapper;
    }

    @Mock
    private NodeService nodeService;

    @InjectMocks
    private NodeController nodeController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(nodeController).build();
        objectMapper = defaultMapper();

    }
    /*
     * @Test
     * void createWellShouldReturnOk() throws Exception {
     * // Arrange
     * WellDTO wellDTO = new WellDTO();
     * wellDTO.setName("Test Well");
     * wellDTO.setState(NodeStateEnum.RUNNING);
     * wellDTO.setStartupDate(Instant.parse("2025-01-01T00:00:00Z"));
     * wellDTO.setLifespanInMonths(240);
     * wellDTO.setUpkeepCosts(MoneyAmount.of("500.00"));
     * wellDTO.setMaintenanceIntervalInDays(30);
     * wellDTO.setOperatingCosts(MoneyAmount.of("200.00"));
     * wellDTO.setWastePercentage(2.5f);
     * 
     * InvestmentCostComponentDTO componentDTO = new InvestmentCostComponentDTO();
     * componentDTO.setName("Drilling");
     * componentDTO.setAmount(MoneyAmount.of("10000.00"));
     * componentDTO.setCostBasis(CostBasisEnum.FLAT);
     * 
     * InvestmentCostDTO investmentCostDTO = new InvestmentCostDTO();
     * investmentCostDTO.setComponents(List.of(componentDTO));
     * wellDTO.setInvestmentCost(investmentCostDTO);
     * 
     * NodeGraphDataDTO graphDataDTO = new NodeGraphDataDTO();
     * graphDataDTO.setXPosition(10.0);
     * graphDataDTO.setYPosition(20.0);
     * graphDataDTO.setCoordinates(0.0);
     * wellDTO.setGraphData(graphDataDTO);
     * 
     * wellDTO.setType(new NodeTypeDataDTO(VerticalEnum.EXTRACTION,
     * StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
     * 
     * wellDTO.setMaxCollectionCapacity(1000.0f);
     * wellDTO.setDeclineCurve(0.05f);
     * wellDTO.setGasRichness(0.3f);
     * wellDTO.setDTMTime(15);
     * wellDTO.setDTMCost(MoneyAmount.of("1500.00"));
     * // 👇 FALTABA ESTO: le decimos al mock de NodeService qué debe devolver
     * Well mockWell = new Well(
     * wellDTO.getName(), wellDTO.getState(), wellDTO.getStartupDate(),
     * wellDTO.getLifespanInMonths(),
     * wellDTO.getUpkeepCosts(), wellDTO.getMaintenanceIntervalInDays(),
     * wellDTO.getOperatingCosts(),
     * wellDTO.getWastePercentage(), null, null, id, null,
     * wellDTO.getMaxCollectionCapacity(), wellDTO.getDeclineCurve(),
     * wellDTO.getGasRichness(),
     * wellDTO.getDTMTime(), wellDTO.getDTMCost());
     * 
     * when(nodeService.saveWell(any(WellDTO.class))).thenReturn(mockWell);
     * 
     * // Act & Assert
     * mockMvc.perform(post("/nodes/well")
     * .contentType(MediaType.APPLICATION_JSON)
     * .content(objectMapper.writeValueAsString(wellDTO)))
     * .andExpect(status().isOk())
     * .andExpect(content().contentType(MediaType.APPLICATION_JSON))
     * .andExpect(result -> {
     * String response = result.getResponse().getContentAsString();
     * assert response.contains("Well created successfully");
     * });
     * }
     */
}