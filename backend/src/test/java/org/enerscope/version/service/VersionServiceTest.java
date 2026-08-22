package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.dto.CreateVersionRequestDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionServiceTest {

    @Mock
    private VersionRepository versionRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AppLogger logger;

    private VersionService versionService;

    @BeforeEach
    void setUp() {
        versionService = new VersionService(versionRepository, projectRepository, logger);
    }

    // ---- createVersion -------------------------------------------------------

    @Test
    void createVersionPersistsAndLinksToProjectWithoutParent() {
        UUID projectId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        Version saved = versionService.createVersion(projectId, new CreateVersionRequestDTO("v1", null));

        assertEquals("v1", saved.getName());
        assertEquals(project, saved.getProject());
        assertNull(saved.getParentVersion());
        assertTrue(project.getVersions().contains(saved));
    }

    @Test
    void createVersionPersistsWithValidParentVersion() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        ReflectionTestUtils.setField(project, "id", projectId);
        Version parent = new Version("v1", project, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.of(parent));
        when(versionRepository.save(any(Version.class))).thenAnswer(inv -> inv.getArgument(0));

        Version saved = versionService.createVersion(projectId, new CreateVersionRequestDTO("v2", parentId));

        assertEquals(parent, saved.getParentVersion());
    }

    @Test
    void createVersionRejectsUnknownProject() {
        UUID projectId = UUID.randomUUID();
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v1", null)));
        verify(versionRepository, never()).save(any());
    }

    @Test
    void createVersionRejectsUnknownParentVersion() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v2", parentId)));
        verify(versionRepository, never()).save(any());
    }

    @Test
    void createVersionRejectsParentVersionFromDifferentProject() {
        UUID projectId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Project project = new Project("Grid Expansion", "Expands the regional grid", new Organization("Acme"));
        ReflectionTestUtils.setField(project, "id", projectId);
        Project otherProject = new Project("Other", "Other project", new Organization("Acme"));
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());
        Version parent = new Version("v1", otherProject, null);
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(versionRepository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThrows(IllegalArgumentException.class, () -> versionService.createVersion(
                projectId, new CreateVersionRequestDTO("v2", parentId)));
        verify(versionRepository, never()).save(any());
    }
}
