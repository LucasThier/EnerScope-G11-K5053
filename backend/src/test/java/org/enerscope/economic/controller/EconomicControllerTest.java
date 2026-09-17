package org.enerscope.economic.controller;

import java.time.Instant;
import java.util.*;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.economic.service.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.common.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EconomicController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class EconomicControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean EconomicService service;
    @MockitoBean SessionService sessions;
    @MockitoBean AppLogger logger;
    final String path="/projects/"+UUID.randomUUID()+"/versions/"+UUID.randomUUID()+"/economics";
    @BeforeEach void authenticate() {
        when(sessions.validate("test-token")).thenReturn(Optional.of(new Session("test-token",
                User.fromJwtClaims(UUID.randomUUID(),"test@example.com","Test","User"),Instant.now().plusSeconds(3600))));
    }
    @Test void anonymousEvaluationReturns401() throws Exception {
        mvc.perform(post(path+"/evaluations")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }
    @Test void configurationRoundTripUsesEnvelope() throws Exception {
        when(service.save(any(),any(),any())).thenReturn(EconomicExample.configuration());
        when(service.get(any(),any())).thenReturn(EconomicExample.configuration());
        mvc.perform(put(path+"/configuration").header("Authorization","Bearer test-token").contentType("application/json")
                .content(EconomicExample.MAPPER.writeValueAsString(EconomicExample.configuration())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.currency").value("USD"));
        mvc.perform(get(path+"/configuration").header("Authorization","Bearer test-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
    }
    @Test void evaluationReturnsCreatedAndHistoryCanBeRead() throws Exception {
        when(service.list(any(),any())).thenReturn(List.of());
        mvc.perform(post(path+"/evaluations").header("Authorization","Bearer test-token")).andExpect(status().isCreated());
        mvc.perform(get(path+"/evaluations").header("Authorization","Bearer test-token")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
        mvc.perform(get(path+"/evaluations/"+UUID.randomUUID()).header("Authorization","Bearer test-token")).andExpect(status().isOk());
    }
    @Test void permissionDenialReturns403() throws Exception {
        when(service.evaluate(any(),any())).thenThrow(new ForbiddenException("Project permission required"));
        mvc.perform(post(path+"/evaluations").header("Authorization","Bearer test-token")).andExpect(status().isForbidden());
    }
    @Test void missingConfigurationReturns404() throws Exception {
        when(service.get(any(),any())).thenThrow(new EntityNotFoundException("Configuration not found"));
        mvc.perform(get(path+"/configuration").header("Authorization","Bearer test-token")).andExpect(status().isNotFound());
    }
    @Test void invalidConfigurationReturns400() throws Exception {
        when(service.save(any(),any(),any())).thenThrow(new IllegalArgumentException("Invalid ownership"));
        mvc.perform(put(path+"/configuration").header("Authorization","Bearer test-token").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
}
