package org.enerscope.version.dto;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;

import java.util.Set;
import java.util.UUID;

public record VersionMemberRoleDTO(
        UUID roleId,
        VersionMemberType memberType,
        VerticalEnum vertical,
        Set<VersionMemberPermission> permissions
) {
    public static VersionMemberRoleDTO from(VersionMemberRole role) {
        return new VersionMemberRoleDTO(
                role.getId(), role.getMemberType(), role.getVertical(), role.getPermissions());
    }
}
