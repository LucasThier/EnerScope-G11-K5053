package org.enerscope.version.dto;

import jakarta.validation.constraints.NotNull;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.enums.VersionMemberType;

/**
 * Turns one existing version role into another in place. The same shape rule
 * as {@link AddVersionMemberRoleRequestDTO} applies: {@code vertical} is
 * required for {@code EDITOR} and must be left out otherwise. There is no
 * {@code userId}: the role is addressed by the path.
 */
public record ChangeVersionMemberRoleRequestDTO(
        @NotNull
        VersionMemberType memberType,

        VerticalEnum vertical
) {}
