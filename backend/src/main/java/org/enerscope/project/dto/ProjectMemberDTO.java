package org.enerscope.project.dto;

import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;

import java.util.Set;
import java.util.UUID;

/**
 * A member of a project, flattened with the identity fields the project screens
 * list (name, mail, job title) so the client never needs a second call per row.
 *
 * <p>{@code active} mirrors the user account state: there is no separate
 * membership status yet, so an invited-but-not-yet-active member cannot be
 * represented (see {@code docs/considerations.md}).</p>
 */
public record ProjectMemberDTO(
        UUID id,
        UUID userId,
        String userMail,
        String firstName,
        String lastName,
        String jobTitle,
        boolean active,
        ProjectMemberType memberType,
        Set<ProjectMemberPermission> permissions
) {}
