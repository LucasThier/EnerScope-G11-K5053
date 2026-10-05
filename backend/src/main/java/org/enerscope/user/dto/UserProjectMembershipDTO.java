package org.enerscope.user.dto;

import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.UUID;

/**
 * One project a user belongs to, as seen from {@code GET /users/{id}}. Only
 * active projects are included — deactivated ones are left out the same way
 * {@code GET /projects} leaves them out. {@code organizationActive} is carried
 * rather than filtered on: a project under a suspended organization still
 * shows, marked, instead of disappearing from the user's record.
 */
public record UserProjectMembershipDTO(
        UUID projectId,
        String projectName,
        UUID organizationId,
        String organizationName,
        boolean organizationActive,
        ProjectMemberType memberType
) {}
