package org.enerscope.version.dto;

import java.util.List;
import java.util.UUID;

/**
 * One row of the project's role matrix: an active version and every member
 * with every role they hold on it. {@code members} is exactly what
 * {@code GET /version/{id}/members} returns for that version.
 */
public record ProjectVersionMembersDTO(
        UUID versionId,
        String versionName,
        List<VersionMemberDTO> members
) {}
