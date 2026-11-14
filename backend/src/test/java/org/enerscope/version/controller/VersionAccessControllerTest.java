package org.enerscope.version.controller;

import org.enerscope.auth.filter.AuthFilter;
import org.enerscope.common.ForbiddenException;
import org.enerscope.config.SecurityConfig;
import org.enerscope.logging.AppLogger;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.session.model.Session;
import org.enerscope.session.service.SessionService;
import org.enerscope.user.model.User;
import org.enerscope.version.dto.ProjectAccessDTO;
import org.enerscope.version.dto.VersionAccessDTO;
import org.enerscope.version.dto.VersionRoleGrantDTO;
import org.enerscope.version.model.enums.VersionAccessSource;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.service.VersionAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VersionAccessController.class)
@Import({SecurityConfig.class, AuthFilter.class})
class VersionAccessControllerTest {

    @Autowired
    MockMvc mvc;
    @MockitoBean
    VersionAccessService service;
    @MockitoBean
    SessionService sessions;
    @MockitoBean
    AppLogger logger;

    private final UUID projectId = UUID.randomUUID();
    private final String path = "/projects/" + projectId + "/access";

    @BeforeEach
    void authenticate() {
        when(sessions.validate("test-token")).thenReturn(Optional.of(new Session("test-token",
                User.fromJwtClaims(UUID.randomUUID(), "test@example.com", "Test", "User"),
                Instant.now().plusSeconds(3600))));
    }

    @Test
    void accessReturnsResolvedCapabilitiesInTheEnvelope() throws Exception {
        UUID versionId = UUID.randomUUID();
        VersionAccessDTO version = new VersionAccessDTO(versionId, "Base", VersionAccessSource.VERSION_ROLES,
                List.of(new VersionRoleGrantDTO(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)), "EDITOR",
                List.of(VerticalEnum.EXTRACTION, VerticalEnum.INTERNAL_CONSUMPTION),
                true, true, true, true, false, true, true, true);
        when(service.resolveForProject(projectId)).thenReturn(new ProjectAccessDTO(projectId, false,
                ProjectMemberType.EDITOR, List.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT),
                false, true, List.of(version)));

        mvc.perform(get(path).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.projectRole").value("EDITOR"))
                .andExpect(jsonPath("$.data.canCreateVersions").value(true))
                .andExpect(jsonPath("$.data.versions[0].versionId").value(versionId.toString()))
                .andExpect(jsonPath("$.data.versions[0].source").value("VERSION_ROLES"))
                .andExpect(jsonPath("$.data.versions[0].roles[0].type").value("EDITOR"))
                .andExpect(jsonPath("$.data.versions[0].roles[0].vertical").value("EXTRACTION"))
                .andExpect(jsonPath("$.data.versions[0].editableVerticals[1]").value("INTERNAL_CONSUMPTION"))
                .andExpect(jsonPath("$.data.versions[0].canSimulate").value(true))
                .andExpect(jsonPath("$.data.versions[0].canManage").value(false));
    }

    @Test
    void accessDeniedReturns403WithTheProjectMessage() throws Exception {
        when(service.resolveForProject(projectId)).thenThrow(new ForbiddenException("You are not allowed to view this project"));

        mvc.perform(get(path).header("Authorization", "Bearer test-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("You are not allowed to view this project"));
    }

    @Test
    void anonymousAccessReturns401() throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
