package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.Project;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.version.dto.CreateVersionRequestDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class VersionService {

    private final VersionRepository versionRepository;
    private final ProjectRepository projectRepository;
    private final AppLogger logger;

    public VersionService(VersionRepository versionRepository,
                           ProjectRepository projectRepository,
                           AppLogger logger) {
        this.versionRepository = versionRepository;
        this.projectRepository = projectRepository;
        this.logger = logger;
    }

    public Version createVersion(UUID projectId, CreateVersionRequestDTO data) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Version parentVersion = null;
        if (data.parentVersionId() != null) {
            parentVersion = versionRepository.findById(data.parentVersionId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent version not found"));
            if (!parentVersion.getProject().getId().equals(projectId)) {
                throw new IllegalArgumentException("Parent version does not belong to this project");
            }
        }

        Version version = new Version(data.name(), project, parentVersion);
        project.addVersion(version);

        Version saved = versionRepository.save(version);
        logger.info("Created version {} for project {}", saved.getName(), project.getName());
        return saved;
    }
}
