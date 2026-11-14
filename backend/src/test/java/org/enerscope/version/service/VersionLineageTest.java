package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VersionLineageTest {

    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AppLogger logger;

    private VersionLineage lineage;

    @BeforeEach
    void setUp() {
        lineage = new VersionLineage(projectRepository, logger);
    }

    @Test
    void versionsOfTheSameProjectPass() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(a)).thenReturn(Optional.of(project));
        when(projectRepository.findOwnerIdByVersionId(b)).thenReturn(Optional.of(project));

        assertDoesNotThrow(() -> lineage.assertSameProject(a, b));
    }

    @Test
    void versionsOfDifferentProjectsAreRejectedWith400() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(a)).thenReturn(Optional.of(UUID.randomUUID()));
        when(projectRepository.findOwnerIdByVersionId(b)).thenReturn(Optional.of(UUID.randomUUID()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> lineage.assertSameProject(a, b));
        assertEquals("The versions belong to different projects", error.getMessage());
    }

    @Test
    void twoVersionsDetachedFromEveryProjectPass() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(a)).thenReturn(Optional.empty());
        when(projectRepository.findOwnerIdByVersionId(b)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> lineage.assertSameProject(a, b));
    }

    @Test
    void aProjectVersionAndADetachedOneAreRejected() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(a)).thenReturn(Optional.of(UUID.randomUUID()));
        when(projectRepository.findOwnerIdByVersionId(b)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> lineage.assertSameProject(a, b));
    }

    @Test
    void aParentInAnotherProjectIsRejectedWithTheParentMessage() {
        UUID child = UUID.randomUUID();
        UUID parent = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(child)).thenReturn(Optional.of(UUID.randomUUID()));
        when(projectRepository.findOwnerIdByVersionId(parent)).thenReturn(Optional.of(UUID.randomUUID()));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> lineage.assertParentInSameProject(child, parent));
        assertEquals("Parent version must belong to the same project", error.getMessage());
    }

    @Test
    void aVersionCannotBecomeItsOwnParent() {
        Version version = version(null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> lineage.assertNotAncestor(version.getId(), version));
        assertEquals("A version cannot be its own ancestor", error.getMessage());
    }

    @Test
    void aVersionCannotHangFromOneOfItsDescendants() {
        Version root = version(null);
        Version child = version(root);
        Version grandchild = version(child);

        assertThrows(IllegalArgumentException.class, () -> lineage.assertNotAncestor(root.getId(), grandchild));
    }

    @Test
    void anUnrelatedChainPassesEvenIfItAlreadyLoops() {
        Version first = version(null);
        Version second = version(first);
        first.setParentVersion(second);

        assertDoesNotThrow(() -> lineage.assertNotAncestor(UUID.randomUUID(), second));
    }

    @Test
    void isSameProjectTellsVersionsOfTheSameAndOfDifferentProjectsApart() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(a)).thenReturn(Optional.of(project));
        when(projectRepository.findOwnerIdByVersionId(b)).thenReturn(Optional.of(project));
        when(projectRepository.findOwnerIdByVersionId(c)).thenReturn(Optional.of(UUID.randomUUID()));

        assertTrue(lineage.isSameProject(a, b));
        assertFalse(lineage.isSameProject(a, c));
    }

    @Test
    void isSameProjectTreatsTwoDetachedVersionsAsTogetherButNotADetachedOneWithAProjectOne() {
        UUID detached = UUID.randomUUID();
        UUID otherDetached = UUID.randomUUID();
        UUID attached = UUID.randomUUID();
        when(projectRepository.findOwnerIdByVersionId(detached)).thenReturn(Optional.empty());
        when(projectRepository.findOwnerIdByVersionId(otherDetached)).thenReturn(Optional.empty());
        when(projectRepository.findOwnerIdByVersionId(attached)).thenReturn(Optional.of(UUID.randomUUID()));

        assertTrue(lineage.isSameProject(detached, otherDetached));
        assertFalse(lineage.isSameProject(detached, attached));
    }

    private static Version version(Version parent) {
        Version version = new Version("v", parent, null, null, null, null);
        ReflectionTestUtils.setField(version, "id", UUID.randomUUID());
        return version;
    }
}
