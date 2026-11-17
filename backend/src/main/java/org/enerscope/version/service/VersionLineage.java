package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.model.Version;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class VersionLineage {

    static final String DIFFERENT_PROJECTS = "The versions belong to different projects";
    static final String PARENT_IN_ANOTHER_PROJECT = "Parent version must belong to the same project";
    static final String OWN_ANCESTOR = "A version cannot be its own ancestor";

    private final ProjectRepository projectRepository;
    private final AppLogger logger;

    public VersionLineage(ProjectRepository projectRepository, AppLogger logger) {
        this.projectRepository = projectRepository;
        this.logger = logger;
    }

    public void assertSameProject(UUID versionId, UUID otherVersionId) {
        assertSameProject(versionId, otherVersionId, DIFFERENT_PROJECTS);
    }

    public void assertParentInSameProject(UUID versionId, UUID parentVersionId) {
        assertSameProject(versionId, parentVersionId, PARENT_IN_ANOTHER_PROJECT);
    }

    public void assertNotAncestor(UUID versionId, Version candidateParent) {
        Set<UUID> visited = new HashSet<>();
        for (Version current = candidateParent; current != null && visited.add(current.getId());
                current = current.getParentVersion()) {
            if (Objects.equals(current.getId(), versionId)) {
                logger.warn("Rejected making version {} a descendant of itself through {}",
                        versionId, candidateParent.getId());
                throw new IllegalArgumentException(OWN_ANCESTOR);
            }
        }
    }

    public boolean isSameProject(UUID versionId, UUID otherVersionId) {
        return projectRepository.findOwnerIdByVersionId(versionId)
                .equals(projectRepository.findOwnerIdByVersionId(otherVersionId));
    }

    private void assertSameProject(UUID versionId, UUID otherVersionId, String message) {
        Optional<UUID> project = projectRepository.findOwnerIdByVersionId(versionId);
        Optional<UUID> otherProject = projectRepository.findOwnerIdByVersionId(otherVersionId);
        if (!project.equals(otherProject)) {
            logger.warn("Rejected linking version {} (project {}) with version {} (project {})",
                    versionId, project.orElse(null), otherVersionId, otherProject.orElse(null));
            throw new IllegalArgumentException(message);
        }
    }
}
