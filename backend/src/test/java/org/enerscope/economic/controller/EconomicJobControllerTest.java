package org.enerscope.economic.controller;
import java.time.Instant;
import java.util.*;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.economic.dto.*;
import org.enerscope.economic.model.enums.EconomicJobStatus;
import org.enerscope.economic.service.EconomicJobService;
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

@WebMvcTest(EconomicJobController.class)
@Import({SecurityConfig.class,AuthFilter.class})
class EconomicJobControllerTest {
    @Autowired
    MockMvc mvc;

    @MockitoBean
    EconomicJobService service;

    @MockitoBean
    SessionService sessions;

    @MockitoBean
    AppLogger logger;

    final UUID project=UUID.randomUUID(),version=UUID.randomUUID(),id=UUID.randomUUID();

    final String path="/projects/"+project+"/versions/"+version+"/economics";

    @BeforeEach
    void authenticate() {
        when(sessions.validate("test")).thenReturn(Optional.of(new Session("test",
            User.fromJwtClaims(UUID.randomUUID(),"test@example.com","Test","User"),Instant.now().plusSeconds(3600))));
    }

    @Test
    void acceptanceReturns202AndSeedAsStringWithoutWaitingForResults() throws Exception {
        when(service.submit(eq(project),eq(version),any())).thenReturn(new EconomicJobDTO(id,EconomicJobStatus.QUEUED,10,0,"9223372036854775807",Instant.now(),null,null,null,null));
        mvc.perform(post(path+"/evaluation-jobs").header("Authorization","Bearer test").contentType("application/json")
                .content("{\"rounds\":10,\"seed\":\"9223372036854775807\"}"))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("QUEUED"))
            .andExpect(jsonPath("$.data.seed").value("9223372036854775807"));
        verify(service).submit(project,version,new EconomicJobRequest(10,"9223372036854775807"));
    }

    @Test
    void anonymousRequestsCannotReadOrStartJobsOrRounds() throws Exception {
        for(var request:List.of(post(path+"/evaluation-jobs"),get(path+"/evaluation-jobs"),get(path+"/evaluation-jobs/"+id),
                post(path+"/evaluation-jobs/"+id+"/retry"),get(path+"/evaluations/"+id+"/rounds"),
                get(path+"/evaluations/"+id+"/rounds/1")))
            mvc.perform(request).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.success").value(false));
        verifyNoInteractions(service);
    }

    @Test
    void readRoutesAndRetryPreserveScopedIdentifiersAndEnvelopes() throws Exception {
        mvc.perform(get(path+"/evaluation-jobs").header("Authorization","Bearer test")).andExpect(status().isOk());
        mvc.perform(get(path+"/evaluation-jobs/"+id).header("Authorization","Bearer test")).andExpect(status().isOk());
        mvc.perform(post(path+"/evaluation-jobs/"+id+"/retry").header("Authorization","Bearer test")).andExpect(status().isAccepted());
        mvc.perform(get(path+"/evaluations/"+id+"/rounds").header("Authorization","Bearer test")).andExpect(status().isOk());
        mvc.perform(get(path+"/evaluations/"+id+"/rounds/2").header("Authorization","Bearer test")).andExpect(status().isOk());
        verify(service).list(project,version);verify(service).get(project,version,id);
        verify(service).retry(project,version,id);verify(service).roundSummaries(project,version,id);verify(service).round(project,version,id,2);
    }

    @Test
    void missingJobPermissionAndInvalidOptionsUseGlobalErrors() throws Exception {
        when(service.get(any(),any(),any())).thenThrow(new EntityNotFoundException("Job not found"));
        when(service.retry(any(),any(),any())).thenThrow(new ForbiddenException("Project permission required"));
        when(service.submit(any(),any(),any())).thenThrow(new IllegalArgumentException("Invalid rounds"));
        mvc.perform(get(path+"/evaluation-jobs/"+id).header("Authorization","Bearer test")).andExpect(status().isNotFound());
        mvc.perform(post(path+"/evaluation-jobs/"+id+"/retry").header("Authorization","Bearer test")).andExpect(status().isForbidden());
        mvc.perform(post(path+"/evaluation-jobs").header("Authorization","Bearer test").contentType("application/json").content("{}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.success").value(false));
    }
}
