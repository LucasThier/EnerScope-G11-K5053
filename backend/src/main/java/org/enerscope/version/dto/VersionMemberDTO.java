package org.enerscope.version.dto;

import java.util.List;
import java.util.UUID;

/**
 * A member of a version, flattened with identity fields, and every role they
 * hold on it. Unlike {@code ProjectMemberDTO} there is no single
 * {@code memberType}/{@code permissions} pair: a version member can hold
 * several roles at once (see {@code VersionMember}).
 */
public record VersionMemberDTO(
        UUID id,
        UUID userId,
        String userMail,
        String firstName,
        String lastName,
        String jobTitle,
        boolean active,
        List<VersionMemberRoleDTO> roles
) {}
