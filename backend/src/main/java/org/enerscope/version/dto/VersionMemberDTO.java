package org.enerscope.version.dto;

import org.enerscope.user.model.User;
import org.enerscope.version.model.VersionMember;

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
) {
    /** The one mapping used by the per-version listing and the project matrix, so both return the same shape. */
    public static VersionMemberDTO from(VersionMember member) {
        User user = member.getUser();
        return new VersionMemberDTO(
                member.getId(),
                user.getId(),
                user.getMail(),
                user.getFirstName(),
                user.getLastName(),
                user.getJobTitle(),
                user.isActive(),
                member.getRoles().stream().map(VersionMemberRoleDTO::from).toList());
    }
}
