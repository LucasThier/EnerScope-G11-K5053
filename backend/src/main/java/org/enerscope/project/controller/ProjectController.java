package org.enerscope.project.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.project.dto.AddProjectMemberRequestDTO;
import org.enerscope.project.dto.CreateProjectRequestDTO;
import org.enerscope.project.dto.ProjectDTO;
import org.enerscope.project.dto.ProjectMemberDTO;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.service.ProjectService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Project-management endpoints. These are not under {@code /auth/**}, so they
 * require a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/projects")
@Tag(name = "Projects", description = "Project management operations")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    @Operation(summary = "Create a project", description = "Create a new project under an organization.")
    public ResponseEntity<ApiResponse<ProjectDTO>> createProject(@Valid @RequestBody CreateProjectRequestDTO data) {
        Project project = projectService.createProject(data);
        return Responses.created("Project created", toDTO(project));
    }

    @PostMapping("/{projectId}/members")
    @Operation(summary = "Add a member to a project",
            description = "Add a user to the project with a given role.")
    public ResponseEntity<ApiResponse<ProjectMemberDTO>> addMember(
            @PathVariable UUID projectId,
            @Valid @RequestBody AddProjectMemberRequestDTO data) {
        ProjectMember member = projectService.addMember(projectId, data);
        return Responses.created("Member added", toDTO(member));
    }

    private ProjectDTO toDTO(Project project) {
        return new ProjectDTO(
                project.getId(), project.getName(), project.getDescription(), project.getOrganization().getId());
    }

    private ProjectMemberDTO toDTO(ProjectMember member) {
        ProjectMemberRole role = member.getRoles().iterator().next();
        return new ProjectMemberDTO(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getMail(),
                role.getMemberType(),
                role.getPermissions());
    }
}
