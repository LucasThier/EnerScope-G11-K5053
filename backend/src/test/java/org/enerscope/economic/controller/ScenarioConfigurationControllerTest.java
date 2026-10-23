package org.enerscope.economic.controller;
import java.time.Instant;
import java.util.*;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.config.SecurityConfig;
import org.enerscope.scenario.dto.ScenarioConfigurationDTO;
import org.enerscope.logging.AppLogger;
import org.enerscope.scenario.controller.ScenarioConfigurationController;
import org.enerscope.scenario.service.ScenarioConfigurationService;
import org.enerscope.scenario.service.ScenarioConflictException;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@WebMvcTest(ScenarioConfigurationController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class ScenarioConfigurationControllerTest {
 @Autowired MockMvc mvc;
 @MockitoBean
 ScenarioConfigurationService service;
 @MockitoBean SessionService sessions;
 @MockitoBean AppLogger logger;
 final String root="/projects/"+UUID.randomUUID()+"/versions";
 final String path=root+"/"+UUID.randomUUID()+"/scenario-configuration";
 @BeforeEach void authenticate(){
  when(sessions.validate("token")).thenReturn(Optional.of(new Session("token",
   User.fromJwtClaims(UUID.randomUUID(),"test@example.com","Test","User"),Instant.now().plusSeconds(3600))));
 }
 @Test void anonymousReadAndSaveAreRejected() throws Exception {
  mvc.perform(get(path)).andExpect(status().isUnauthorized());
  mvc.perform(put(path).contentType("application/json").content("{}")).andExpect(status().isUnauthorized());
  verifyNoInteractions(service);
 }
 @Test void readAndReadinessUseResponseEnvelope() throws Exception {
  when(service.get(any(),any())).thenReturn(new ScenarioConfigurationDTO("token","source",null,List.of(),null,null,List.of()));
  when(service.readiness(any())).thenReturn(List.of());
  mvc.perform(get(path).header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(jsonPath("$.data.token").value("token"));
  mvc.perform(get(root+"/simulation-readiness").header("Authorization","Bearer token")).andExpect(status().isOk()).andExpect(jsonPath("$.data").isArray());
 }
 @Test void staleSaveReturnsConflictAndPermissionFailureReturnsForbidden() throws Exception {
  when(service.save(any(),any(),any())).thenThrow(new ScenarioConflictException());
  mvc.perform(put(path).header("Authorization","Bearer token").contentType("application/json").content("{}")).andExpect(status().isConflict()).andExpect(jsonPath("$.success").value(false));
  doThrow(new ForbiddenException("Read only")).when(service).save(any(),any(),any());
  mvc.perform(put(path).header("Authorization","Bearer token").contentType("application/json").content("{}")).andExpect(status().isForbidden());
 }
 @Test void discardRequiresAndPassesConcurrencyToken() throws Exception {
  mvc.perform(delete(path+"/draft").header("Authorization","Bearer token").header("If-Match","revision")).andExpect(status().isOk());
  verify(service).discard(any(),any(),eq("revision"));
  mvc.perform(delete(path+"/draft").header("Authorization","Bearer token")).andExpect(status().isBadRequest());
 }
}
