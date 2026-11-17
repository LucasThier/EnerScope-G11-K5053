package org.enerscope.project.model;

import org.enerscope.project.model.enums.ProjectMemberPermission;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProjectMemberTest {

    @Test
    void executiveHasOnlyViewProject() {
        assertEquals(Set.of(ProjectMemberPermission.VIEW_PROJECT),
                ProjectMember.defaultPermissionsFor(ProjectMemberType.EXECUTIVE));
    }

    @Test
    void everyMemberTypeHasDefaultPermissions() {
        for (ProjectMemberType type : ProjectMemberType.values()) {
            assertNotNull(ProjectMember.defaultPermissionsFor(type), type + " has no default permissions");
        }
    }

    @Test
    void adminAndEditorKeepTheirPermissions() {
        assertEquals(EnumSet.allOf(ProjectMemberPermission.class),
                ProjectMember.defaultPermissionsFor(ProjectMemberType.ADMIN));
        assertEquals(Set.of(ProjectMemberPermission.EDIT_PROJECT, ProjectMemberPermission.VIEW_PROJECT),
                ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR));
    }
}
