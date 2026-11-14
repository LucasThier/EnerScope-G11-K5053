package org.enerscope.version.service;

import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.version.model.enums.VersionMemberPermission;

public enum VersionCapability {
    VIEW(VersionMemberPermission.VIEW_VERSION, null),
    EDIT(VersionMemberPermission.EDIT_VERSION, ProjectMemberPermission.EDIT_PROJECT),
    EDIT_CONNECTIONS(VersionMemberPermission.EDIT_VERSION, ProjectMemberPermission.EDIT_PROJECT),
    SIMULATE(VersionMemberPermission.SIMULATE_VERSION, ProjectMemberPermission.EDIT_PROJECT),
    MANAGE(VersionMemberPermission.MANAGE_VERSION, ProjectMemberPermission.MANAGE_PROJECT);

    private final VersionMemberPermission versionPermission;
    private final ProjectMemberPermission projectPermission;

    VersionCapability(VersionMemberPermission versionPermission, ProjectMemberPermission projectPermission) {
        this.versionPermission = versionPermission;
        this.projectPermission = projectPermission;
    }

    public VersionMemberPermission versionPermission() {
        return versionPermission;
    }

    public ProjectMemberPermission projectPermission() {
        return projectPermission;
    }
}
