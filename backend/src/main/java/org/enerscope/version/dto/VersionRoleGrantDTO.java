package org.enerscope.version.dto;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.enums.VersionMemberType;

public record VersionRoleGrantDTO(VersionMemberType type, VerticalEnum vertical) {}
