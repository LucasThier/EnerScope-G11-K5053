package org.enerscope.version.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.enums.VersionMemberType;

import java.util.UUID;

/**
 * Grants a user a role on a version. {@code vertical} is required when
 * {@code memberType} is {@code EDITOR} (the vertical the editor may touch)
 * and must be left out otherwise — {@code ADMIN}/{@code SIMULATOR} apply to
 * the whole version. Adds the role; it never replaces roles the user
 * already has on this version (see {@code VersionMember}).
 */
public record AddVersionMemberRoleRequestDTO(
        @NotNull
        UUID userId,

        @NotNull
        VersionMemberType memberType,

        VerticalEnum vertical
) {}
