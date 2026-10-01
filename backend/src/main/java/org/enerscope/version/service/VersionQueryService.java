package org.enerscope.version.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.enerscope.common.*;
import org.enerscope.logging.AppLogger;
import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.repository.ProjectRepository;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.util.AuthUtil;
import org.enerscope.version.dto.VersionSummaryDTO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VersionQueryService {
    private final ProjectRepository projects;
    private final AppLogger logger;

    @Transactional(readOnly = true)
    public List<VersionSummaryDTO> list(UUID projectId) {
        var session = AuthUtil.currentSession();
        if (session == null || session.getUser() == null) throw new UnauthorizedException("Authentication required");
        var project = projects.findById(projectId)
                .orElseThrow(() -> new EntityNotFoundException("Project not found"));
        var user = session.getUser();
        if (user.getPlatformRole() != PlatformRole.ADMIN) {
            boolean allowed = project.getMembers().stream()
                    .filter(m -> m.isActive() && m.getUser().getId().equals(user.getId()))
                    .flatMap(m -> m.getRoles().stream()).filter(BaseEntity::isActive)
                    .anyMatch(r -> r.getPermissions().contains(ProjectMemberPermission.VIEW_PROJECT));
            if (!allowed) throw new ForbiddenException("Project permission required: VIEW_PROJECT");
        }
        logger.debug("Listing active versions for project {}", projectId);
        return project.getVersions().stream().filter(BaseEntity::isActive)
                .map(v -> new VersionSummaryDTO(v.getId(), v.getName(), v.getLastModified(),
                        v.getNodeSnapshot() == null ? List.of() : v.getNodeSnapshot().stream()
                                .map(n -> new VersionSummaryDTO.NodeSummary(n.getId(), n.getName(),
                                        n.getType() == null || n.getType().getNodeType() == null
                                                ? "UNKNOWN" : n.getType().getNodeType().name()))
                                .toList()))
                .sorted(Comparator.comparing(VersionSummaryDTO::lastModified,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }
}
