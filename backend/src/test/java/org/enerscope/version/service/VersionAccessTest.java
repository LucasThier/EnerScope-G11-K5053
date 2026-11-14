package org.enerscope.version.service;

import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionAccessSource;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionAccessTest {

    @Test
    void platformAdminHoldsEveryCapabilityOnEveryVertical() {
        VersionAccess access = VersionAccess.platformAdmin();

        assertEquals(VersionAccessSource.PLATFORM_ADMIN, access.source());
        for (VersionCapability capability : VersionCapability.values()) {
            assertTrue(access.can(capability), capability.name());
        }
        assertEquals(List.of(VerticalEnum.values()), access.editableVerticals());
    }

    @Test
    void versionAdminHoldsEveryCapabilityOnEveryVertical() {
        VersionAccess access = VersionAccess.fromVersionRoles(List.of(role(VersionMemberType.ADMIN, null)));

        assertEquals(VersionAccessSource.VERSION_ROLES, access.source());
        for (VersionCapability capability : VersionCapability.values()) {
            assertTrue(access.can(capability), capability.name());
        }
        assertEquals(List.of(VerticalEnum.values()), access.editableVerticals());
    }

    @Test
    void versionEditorEditsOnlyItsVerticalPlusInternalConsumption() {
        VersionAccess access = VersionAccess.fromVersionRoles(List.of(role(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION)));

        assertEquals(List.of(VerticalEnum.EXTRACTION, VerticalEnum.INTERNAL_CONSUMPTION), access.editableVerticals());
        assertTrue(access.can(VersionCapability.EDIT));
        assertTrue(access.can(VersionCapability.EDIT_CONNECTIONS));
        assertFalse(access.can(VersionCapability.MANAGE));
    }

    @Test
    void versionEditorCanSimulateWithoutAPersistedSimulatePermission() {
        VersionMemberRole editor = role(VersionMemberType.EDITOR, VerticalEnum.EXPORT);

        assertFalse(editor.getPermissions().contains(VersionMemberPermission.SIMULATE_VERSION));
        assertTrue(VersionAccess.fromVersionRoles(List.of(editor)).can(VersionCapability.SIMULATE));
    }

    @Test
    void versionSimulatorViewsAndSimulatesButCannotEditOrManage() {
        VersionAccess access = VersionAccess.fromVersionRoles(List.of(role(VersionMemberType.SIMULATOR, null)));

        assertTrue(access.can(VersionCapability.VIEW));
        assertTrue(access.can(VersionCapability.SIMULATE));
        assertFalse(access.can(VersionCapability.EDIT));
        assertFalse(access.can(VersionCapability.EDIT_CONNECTIONS));
        assertFalse(access.can(VersionCapability.MANAGE));
        assertTrue(access.editableVerticals().isEmpty());
    }

    @Test
    void severalVersionRolesAddUpTheirCapabilities() {
        VersionAccess access = VersionAccess.fromVersionRoles(List.of(
                role(VersionMemberType.EDITOR, VerticalEnum.EXPORT),
                role(VersionMemberType.EDITOR, VerticalEnum.LIQUEFACTION),
                role(VersionMemberType.SIMULATOR, null)));

        assertEquals(List.of(VerticalEnum.LIQUEFACTION, VerticalEnum.EXPORT, VerticalEnum.INTERNAL_CONSUMPTION),
                access.editableVerticals());
        assertTrue(access.can(VersionCapability.SIMULATE));
        assertFalse(access.can(VersionCapability.MANAGE));
    }

    @Test
    void projectEditorFallbackEditsEveryVerticalAndSimulatesButCannotManage() {
        VersionAccess access = VersionAccess.fromProjectMembership(Optional.of(projectMember(ProjectMemberType.EDITOR)));

        assertEquals(VersionAccessSource.PROJECT_FALLBACK, access.source());
        assertTrue(access.can(VersionCapability.VIEW));
        assertTrue(access.can(VersionCapability.EDIT));
        assertTrue(access.can(VersionCapability.SIMULATE));
        assertFalse(access.can(VersionCapability.MANAGE));
        assertEquals(List.of(VerticalEnum.values()), access.editableVerticals());
    }

    @Test
    void projectAdminFallbackCanManage() {
        VersionAccess access = VersionAccess.fromProjectMembership(Optional.of(projectMember(ProjectMemberType.ADMIN)));

        assertTrue(access.can(VersionCapability.MANAGE));
    }

    @Test
    void nonMemberFallbackHoldsNoCapability() {
        VersionAccess access = VersionAccess.fromProjectMembership(Optional.empty());

        for (VersionCapability capability : VersionCapability.values()) {
            assertFalse(access.can(capability), capability.name());
        }
        assertTrue(access.editableVerticals().isEmpty());
    }

    @Test
    void projectMemberWhoseRoleLacksEveryPermissionCanStillViewButNothingElse() {
        ProjectMember member = new ProjectMember(new User("jane@enerscope.org", "Jane", "Doe", "hashed"),
                new Project("Grid", "Grid", new Organization("Acme")));
        member.addRole(new ProjectMemberRole("Custom", ProjectMemberType.EDITOR, Set.of()));

        VersionAccess access = VersionAccess.fromProjectMembership(Optional.of(member));

        assertTrue(access.can(VersionCapability.VIEW));
        assertTrue(Arrays.stream(VersionCapability.values())
                .filter(capability -> capability != VersionCapability.VIEW)
                .noneMatch(access::can));
    }

    private VersionMemberRole role(VersionMemberType type, VerticalEnum vertical) {
        return new VersionMemberRole(type.name(), type, EnumSet.copyOf(VersionMember.defaultPermissionsFor(type)), vertical);
    }

    private ProjectMember projectMember(ProjectMemberType type) {
        ProjectMember member = new ProjectMember(new User("jane@enerscope.org", "Jane", "Doe", "hashed"),
                new Project("Grid", "Grid", new Organization("Acme")));
        member.addRole(new ProjectMemberRole(type.name(), type, ProjectMember.defaultPermissionsFor(type)));
        return member;
    }
}
