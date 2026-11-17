package org.enerscope.version.dto;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.version.model.enums.VersionAccessSource;

import java.util.List;
import java.util.UUID;

public record VersionAccessDTO(
        UUID versionId,
        String versionName,
        VersionAccessSource source,
        List<VersionRoleGrantDTO> roles,
        String effectiveRole,
        List<VerticalEnum> editableVerticals,
        boolean canView,
        boolean canEdit,
        boolean canEditConnections,
        boolean canSimulate,
        boolean canManage,
        boolean canManageRoles,
        boolean canViewEconomicResults,
        boolean canViewEconomicConfiguration,
        boolean canEditEconomicConfiguration
) {}
