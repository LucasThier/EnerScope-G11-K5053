package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.jwt.JwtService;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Create/edit of an FLNG unit stores and returns shipCapacity and gasConsumption independently. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class FlngUnitShipAndGasHttpTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    EntityManager em;
    @Autowired
    JwtService jwt;
    @Autowired
    ObjectMapper mapper;

    private AccessHttpFixture fixture;
    private String auth;

    @BeforeEach
    void setUp() {
        fixture = new AccessHttpFixture(em, jwt);
        auth = fixture.bearer(fixture.user(PlatformRole.ADMIN));
    }

    @Test
    void createStoresAndReturnsShipCapacityAndGasConsumptionSeparately() throws Exception {
        UUID id = createFlng(3, 30.5);

        assertDetail(id, 3, 30.5);
    }

    @Test
    void editStoresBothValuesAndOneDoesNotOverwriteTheOther() throws Exception {
        UUID id = createFlng(3, 30.5);

        id = editFlng(id, 8, 44.25);
        assertDetail(id, 8, 44.25);

        id = editFlng(id, 8, 11.5); // only gasConsumption changes
        assertDetail(id, 8, 11.5);

        id = editFlng(id, 2, 11.5); // only shipCapacity changes
        assertDetail(id, 2, 11.5);
    }

    private UUID createFlng(int shipCapacity, double gasConsumption) throws Exception {
        String response = mvc.perform(post("/version/" + fixture.version().getId() + "/node")
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(shipCapacity, gasConsumption, true)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).path("data").path("id").asText());
    }

    private UUID editFlng(UUID id, int shipCapacity, double gasConsumption) throws Exception {
        String response = mvc.perform(patch("/version/" + fixture.version().getId() + "/node/" + id)
                        .header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content(body(shipCapacity, gasConsumption, false)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(mapper.readTree(response).path("data").path("id").asText());
    }

    private void assertDetail(UUID id, int shipCapacity, double gasConsumption) throws Exception {
        em.flush();
        em.clear();
        mvc.perform(get("/version/" + fixture.version().getId() + "/node/" + id).header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.attributes.shipCapacity").value(shipCapacity))
                .andExpect(jsonPath("$.data.attributes.gasConsumption").value(gasConsumption));
    }

    private static String body(int shipCapacity, double gasConsumption, boolean withIdentity) {
        return "{\"name\":\"FLNG " + UUID.randomUUID() + "\",\"state\":\"PROPOSED\","
                + "\"startupDate\":\"2025-01-01T00:00:00Z\",\"lifespanInMonths\":120,\"upkeepCosts\":100,"
                + "\"maintenanceIntervalInDays\":30,\"operatingCosts\":50,\"wastePercentage\":0.1,"
                + "\"investmentCost\":{\"components\":[{\"name\":\"Build\",\"amount\":1000,\"costBasis\":\"FLAT\"}]},"
                + "\"graphData\":{\"graphPosition\":{\"x\":1,\"y\":2}},"
                + "\"type\":{\"nodeType\":\"FLNG_UNIT\"},"
                + (withIdentity ? "\"identity\":\"" + UUID.randomUUID() + "\"," : "")
                + "\"maxProcessingCapacity\":140,\"mtpaRatio\":1,\"intermediateStorage\":80,\"vesselDepth\":20,"
                + "\"hiringCost\":1000,\"shipCapacity\":" + shipCapacity + ",\"gasConsumption\":" + gasConsumption + "}";
    }
}
