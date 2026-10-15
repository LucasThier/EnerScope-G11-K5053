package org.enerscope.version.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.dto.NodeBasicsDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.project.service.ProjectAccessService;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.service.VersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Endpoint tests for {@link VersionController}. Uses {@code @WebMvcTest} with
 * the real {@link SecurityConfig}/{@link AuthFilter}, like
 * {@code ProjectControllerTest}. {@link VersionService} and
 * {@link ProjectAccessService} are mocked: these tests verify that every
 * endpoint resolves the owning project and is gated by it (401/403), not the
 * authorization logic itself (covered by {@code ProjectAccessServiceTest}).
 */
@WebMvcTest(VersionController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class VersionControllerTest {

    private static final String ACCESS_TOKEN = "access-token-xyz";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VersionService versionService;
    @MockitoBean
    private ProjectAccessService projectAccessService;
    @MockitoBean
    private SessionService sessionService;
    // GlobalExceptionHandler (@ControllerAdvice) needs an AppLogger bean to load.
    @MockitoBean
    private AppLogger logger;

    @BeforeEach
    void setUp() {
        User caller = User.fromJwtClaims(UUID.randomUUID(), "jane@enerscope.org", "Jane", "Doe");
        Session session = new Session(ACCESS_TOKEN, caller, Instant.now().plusSeconds(3600));
        when(sessionService.validate(ACCESS_TOKEN)).thenReturn(Optional.of(session));
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    // A minimal payload that Jackson's JsonTypeInfo.Id.DEDUCTION can resolve to
    // WellDTO: maxCollectionCapacity is a field unique to it among BaseNodeDTO's
    // subtypes, so an all-null/empty BaseNodeDTO body is ambiguous and rejected.
    private static final String WELL_NODE_JSON =
            "{\"name\":\"Well A\",\"maxCollectionCapacity\":1.0,\"declineCurve\":1.0,"
                    + "\"gasRichness\":1.0,\"dtmTime\":1,\"dtmCost\":1.0,\"surface\":1.0}";

    // ---- GET /version/{id} ----------------------------------------------------

    @Test
    void getVersionReturnsVersionWhenAuthorized() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        Version version = new Version();
        version.setName("Baseline");
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        when(versionService.getVersion(versionId)).thenReturn(version);

        mockMvc.perform(get("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Baseline"));

        verify(projectAccessService).assertCanView(projectId);
    }

    @Test
    void getVersionRejectsCallerWithoutViewAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to view this project"))
                .when(projectAccessService).assertCanView(projectId);

        mockMvc.perform(get("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).getVersion(any());
    }

    @Test
    void getVersionRequiresAuthenticationWith401() throws Exception {
        mockMvc.perform(get("/version/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());

        verify(versionService, never()).getVersion(any());
    }

    // ---- DELETE /version/{id} --------------------------------------------------

    @Test
    void deleteVersionRequiresManageAuthority() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);

        mockMvc.perform(delete("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isOk());

        verify(projectAccessService).assertCanManage(projectId);
        verify(versionService).deleteVersion(versionId);
    }

    @Test
    void deleteVersionRejectsCallerWithoutManageAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to manage this project"))
                .when(projectAccessService).assertCanManage(projectId);

        mockMvc.perform(delete("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).deleteVersion(any());
    }

    // ---- PATCH /version/{id} ---------------------------------------------------

    @Test
    void modifyVersionRequiresEditAuthority() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        when(versionService.modifyVersion(eq(versionId), any(VersionDTO.class))).thenReturn(new Version());

        mockMvc.perform(patch("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VersionDTO("Renamed", null))))
                .andExpect(status().isOk());

        verify(projectAccessService).assertCanEdit(projectId);
    }

    @Test
    void modifyVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(patch("/version/" + versionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new VersionDTO("Renamed", null))))
                .andExpect(status().isForbidden());

        verify(versionService, never()).modifyVersion(any(), any());
    }

    // ---- GET /version/{versionId}/diagram -------------------------------------

    @Test
    void getDiagramRejectsCallerWithoutViewAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to view this project"))
                .when(projectAccessService).assertCanView(projectId);

        mockMvc.perform(get("/version/" + versionId + "/diagram")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).getDiagram(any());
    }

    // ---- GET /version/{versionId}/node/{nodeId} -------------------------------

    @Test
    void getNodeDetailRejectsCallerWithoutViewAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to view this project"))
                .when(projectAccessService).assertCanView(projectId);

        mockMvc.perform(get("/version/" + versionId + "/node/" + nodeId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).getNodeDetail(any(), any());
    }

    // ---- PATCH .../node/{nodeId}/position --------------------------------------

    @Test
    void updateNodePositionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(patch("/version/" + versionId + "/node/" + nodeId + "/position")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NodeGraphDataDTO())))
                .andExpect(status().isForbidden());

        verify(versionService, never()).updateNodePosition(any(), any(), any());
    }

    // ---- PATCH .../node/{nodeId}/basics -----------------------------------------

    @Test
    void updateNodeBasicsRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(patch("/version/" + versionId + "/node/" + nodeId + "/basics")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new NodeBasicsDTO())))
                .andExpect(status().isForbidden());

        verify(versionService, never()).updateNodeBasics(any(), any(), any());
    }

    // ---- POST .../node ----------------------------------------------------------

    @Test
    void addNodeToVersionRequiresEditAuthority() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);

        mockMvc.perform(post("/version/" + versionId + "/node")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WELL_NODE_JSON))
                .andExpect(status().isOk());

        verify(projectAccessService).assertCanEdit(projectId);
    }

    @Test
    void addNodeToVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(post("/version/" + versionId + "/node")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WELL_NODE_JSON))
                .andExpect(status().isForbidden());

        verify(versionService, never()).addNodeToVersion(any(), any());
    }

    // ---- POST .../connection ------------------------------------------------

    @Test
    void addConnectionToVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(post("/version/" + versionId + "/connection")
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConnectionDTO())))
                .andExpect(status().isForbidden());

        verify(versionService, never()).addConnectionToVersion(any(), any());
    }

    // ---- PATCH .../node/{nodeId} --------------------------------------------

    @Test
    void editNodeInVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(patch("/version/" + versionId + "/node/" + nodeId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(WELL_NODE_JSON))
                .andExpect(status().isForbidden());

        verify(versionService, never()).editNodeInVersion(any(), any(), any());
    }

    // ---- PATCH .../connection/{connectionId} --------------------------------

    @Test
    void editConnectionInVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(patch("/version/" + versionId + "/connection/" + connectionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(new ConnectionDTO())))
                .andExpect(status().isForbidden());

        verify(versionService, never()).editConnectionInVersion(any(), any(), any());
    }

    // ---- DELETE .../node/{nodeId} --------------------------------------------

    @Test
    void deleteNodeFromVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID nodeId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(delete("/version/" + versionId + "/node/" + nodeId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).deleteNodeFromVersion(any(), any());
    }

    // ---- DELETE .../connection/{connectionId} -------------------------------

    @Test
    void deleteConnectionFromVersionRejectsCallerWithoutEditAuthorityWith403() throws Exception {
        UUID versionId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(projectAccessService.resolveProjectIdForVersion(versionId)).thenReturn(projectId);
        doThrow(new ForbiddenException("You are not allowed to edit this project"))
                .when(projectAccessService).assertCanEdit(projectId);

        mockMvc.perform(delete("/version/" + versionId + "/connection/" + connectionId)
                        .header("Authorization", "Bearer " + ACCESS_TOKEN))
                .andExpect(status().isForbidden());

        verify(versionService, never()).deleteConnectionFromVersion(any(), any());
    }
}
