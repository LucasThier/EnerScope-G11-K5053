package org.enerscope.version.repository;

import jakarta.persistence.EntityManager;
import org.enerscope.common.ForbiddenException;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.session.model.Session;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.dto.VersionAccessDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionAccessSource;
import org.enerscope.version.model.enums.VersionMemberPermission;
import org.enerscope.version.model.enums.VersionMemberType;
import org.enerscope.version.service.VersionAccessService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "economic.worker.enabled=false",
        "spring.jpa.properties.hibernate.generate_statistics=true"})
@Transactional
class VersionMemberRepositoryPostgreSqlIT {

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url = System.getenv("ECONOMIC_TEST_DATABASE_URL");
        if (url == null || !url.contains("enerscope_economic_test")) {
            throw new IllegalStateException("Set ECONOMIC_TEST_DATABASE_URL to a disposable enerscope_economic_test database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_USER", "postgres"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("ECONOMIC_TEST_DATABASE_PASSWORD", ""));
    }

    @Autowired
    EntityManager em;
    @Autowired
    VersionMemberRepository repository;
    @Autowired
    VersionAccessService accessService;

    private Project project;
    private Version first;
    private Version second;
    private Version third;
    private User caller;
    private User colleague;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization("Version member IT " + UUID.randomUUID());
        em.persist(organization);
        first = version("First");
        second = version("Second");
        third = version("Third");
        project = new Project("Version member IT", "Disposable fixture", organization);
        project.addVersion(first);
        project.addVersion(second);
        project.addVersion(third);
        em.persist(project);
        caller = user();
        colleague = user();
        joinProject(caller);
        joinProject(colleague);
        grant(caller, first, role(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION), role(VersionMemberType.SIMULATOR, null));
        grant(caller, second, role(VersionMemberType.ADMIN, null));
        grant(caller, third, role(VersionMemberType.SIMULATOR, null));
        grant(colleague, first, role(VersionMemberType.ADMIN, null));
        em.flush();
        em.clear();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findsOnlyTheCallersMembershipsInTheRequestedVersionsWithTheirRoles() {
        List<VersionMember> members = repository.findByUserIdAndVersionIdIn(caller.getId(), List.of(first.getId(), second.getId()));

        assertEquals(2, members.size());
        Map<UUID, Set<VersionMemberType>> rolesByVersion = members.stream().collect(Collectors.toMap(
                member -> member.getVersion().getId(),
                member -> member.getRoles().stream().map(VersionMemberRole::getMemberType).collect(Collectors.toSet())));
        assertEquals(Set.of(VersionMemberType.EDITOR, VersionMemberType.SIMULATOR), rolesByVersion.get(first.getId()));
        assertEquals(Set.of(VersionMemberType.ADMIN), rolesByVersion.get(second.getId()));
        assertTrue(members.stream().allMatch(member -> member.getUser().getId().equals(caller.getId())));
    }

    @Test
    void returnsNothingForAUserWithoutVersionRoles() {
        User stranger = user();
        em.flush();

        assertTrue(repository.findByUserIdAndVersionIdIn(stranger.getId(),
                List.of(first.getId(), second.getId(), third.getId())).isEmpty());
    }

    @Test
    void effectiveLookupReturnsTheRowOfACurrentProjectMember() {
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isPresent());
    }

    @Test
    void effectiveLookupReturnsTheRowWhileTheProjectMembershipIsActive() {
        assertTrue(projectMemberOf(caller).isActive());

        VersionMember effective = repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).orElseThrow();

        assertEquals(first.getId(), effective.getVersion().getId());
        assertEquals(caller.getId(), effective.getUser().getId());
        assertEquals(Set.of(VersionMemberType.EDITOR, VersionMemberType.SIMULATOR),
                effective.getRoles().stream().map(VersionMemberRole::getMemberType).collect(Collectors.toSet()));
    }

    @Test
    void effectiveLookupIgnoresTheRowOnceTheProjectMembershipIsDeactivated() {
        projectMemberOf(caller).deactivate();
        em.flush();
        em.clear();

        assertTrue(repository.findByVersionIdAndUserId(first.getId(), caller.getId()).isPresent());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isEmpty());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(second.getId(), caller.getId()).isEmpty());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), colleague.getId()).isPresent());
    }

    @Test
    void effectiveLookupFindsTheRowAgainOnceTheProjectMembershipIsReactivated() {
        ProjectMember membership = projectMemberOf(caller);
        membership.deactivate();
        em.flush();
        em.clear();
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isEmpty());

        em.find(ProjectMember.class, membership.getId()).activate();
        em.flush();
        em.clear();

        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isPresent());
    }

    @Test
    void effectiveLookupIgnoresTheRowsOfADeactivatedVersionOnly() {
        em.find(Version.class, first.getId()).deactivate();
        em.flush();
        em.clear();

        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isEmpty());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), colleague.getId()).isEmpty());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(second.getId(), caller.getId()).isPresent());
    }

    @Test
    void effectiveLookupIgnoresEveryRowOfADeactivatedProject() {
        em.find(Project.class, project.getId()).deactivate();
        em.flush();
        em.clear();

        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), caller.getId()).isEmpty());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(second.getId(), caller.getId()).isEmpty());
    }

    @Test
    void memberListingKeepsTheRowOfADeactivatedProjectMember() {
        projectMemberOf(caller).deactivate();
        em.flush();
        em.clear();

        Set<UUID> listed = repository.findByVersionIdWithUser(first.getId()).stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        assertEquals(Set.of(caller.getId(), colleague.getId()), listed);
    }

    @Test
    void accessResolutionTreatsADeactivatedProjectMemberAsHavingNoAccess() {
        projectMemberOf(caller).deactivate();
        em.flush();
        em.clear();
        authenticate(caller);

        assertThrows(ForbiddenException.class, () -> accessService.resolveForProject(project.getId()));
    }

    @Test
    void effectiveLookupIgnoresTheRowOfSomeoneOutsideTheProject() {
        User orphan = user();
        grant(orphan, first, role(VersionMemberType.ADMIN, null));
        em.flush();
        em.clear();

        assertTrue(repository.findByVersionIdAndUserId(first.getId(), orphan.getId()).isPresent());
        assertTrue(repository.findEffectiveByVersionIdAndUserId(first.getId(), orphan.getId()).isEmpty());
    }

    @Test
    void memberListingLeavesOutRowsOfPeopleOutsideTheProject() {
        User orphan = user();
        grant(orphan, first, role(VersionMemberType.ADMIN, null));
        em.flush();
        em.clear();

        Set<UUID> listed = repository.findByVersionIdWithUser(first.getId()).stream()
                .map(member -> member.getUser().getId())
                .collect(Collectors.toSet());

        assertEquals(Set.of(caller.getId(), colleague.getId()), listed);
    }

    @Test
    void findsEveryRowOfTheUserInTheGivenProjectsAndNoOtherUsersRows() {
        List<VersionMember> members = repository.findByUserIdAndProjectIdIn(caller.getId(), List.of(project.getId()));

        assertEquals(Set.of(first.getId(), second.getId(), third.getId()),
                members.stream().map(member -> member.getVersion().getId()).collect(Collectors.toSet()));
        assertTrue(members.stream().allMatch(member -> member.getUser().getId().equals(caller.getId())));
        assertTrue(repository.findByUserIdAndProjectIdIn(caller.getId(), List.of(UUID.randomUUID())).isEmpty());
    }

    @Test
    void accessResolutionReadsVersionRolesFromMigratedSchema() {
        authenticate(caller);

        List<VersionAccessDTO> versions = accessService.resolveForProject(project.getId()).versions();

        Map<UUID, VersionAccessDTO> byId = versions.stream().collect(Collectors.toMap(VersionAccessDTO::versionId, v -> v));
        assertEquals(3, byId.size());
        assertTrue(byId.values().stream().allMatch(v -> v.source() == VersionAccessSource.VERSION_ROLES));
        assertEquals("EDITOR", byId.get(first.getId()).effectiveRole());
        assertEquals(List.of(VerticalEnum.EXTRACTION, VerticalEnum.INTERNAL_CONSUMPTION), byId.get(first.getId()).editableVerticals());
        assertTrue(byId.get(second.getId()).canManage());
        assertEquals("SIMULATOR", byId.get(third.getId()).effectiveRole());
        assertTrue(byId.get(third.getId()).canSimulate());
        assertTrue(byId.get(third.getId()).editableVerticals().isEmpty());
    }

    private ProjectMember projectMemberOf(User user) {
        return em.createQuery("select m from ProjectMember m where m.user.id = :user and m.project.id = :project",
                        ProjectMember.class)
                .setParameter("user", user.getId())
                .setParameter("project", project.getId())
                .getSingleResult();
    }

    private Version version(String name) {
        Version version = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        return version;
    }

    /**
     * V24 backfills SIMULATE_VERSION on EDITOR roles and must be safe to run
     * again: a legacy EDITOR row (EDIT + VIEW only) gains the permission, a
     * second execution changes nothing, and no other role type is touched.
     */
    @Test
    void v24BackfillsEditorSimulatePermissionAndIsIdempotent() throws Exception {
        User legacy = user();
        joinProject(legacy);
        VersionMemberRole legacyEditor = new VersionMemberRole("EDITOR", VersionMemberType.EDITOR,
                EnumSet.of(VersionMemberPermission.EDIT_VERSION, VersionMemberPermission.VIEW_VERSION),
                VerticalEnum.EXPORT);
        grant(legacy, first, legacyEditor);
        em.flush();
        String sql = new String(new org.springframework.core.io.ClassPathResource(
                "db/migration/V24__editor_simulate_permission.sql").getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);

        long simulatorsBefore = countPermissionRows("SIMULATOR", "SIMULATE_VERSION");
        long adminsBefore = countPermissionRows("ADMIN", "MANAGE_VERSION");
        int firstRun = em.createNativeQuery(sql).executeUpdate();
        long afterFirst = countAllPermissionRows();
        int secondRun = em.createNativeQuery(sql).executeUpdate();

        assertTrue(firstRun >= 0);
        assertEquals(0, secondRun, "a second execution must insert nothing");
        assertEquals(afterFirst, countAllPermissionRows());
        em.clear();
        assertTrue(em.find(VersionMemberRole.class, legacyEditor.getId()).getPermissions()
                .contains(VersionMemberPermission.SIMULATE_VERSION));
        assertEquals(countEditorRoles(), countPermissionRows("EDITOR", "SIMULATE_VERSION"));
        assertEquals(simulatorsBefore, countPermissionRows("SIMULATOR", "SIMULATE_VERSION"));
        assertEquals(adminsBefore, countPermissionRows("ADMIN", "MANAGE_VERSION"));
    }

    private long countPermissionRows(String memberType, String permission) {
        return ((Number) em.createNativeQuery("""
                SELECT count(*) FROM version_member_role_permission p
                JOIN version_member_role r ON r.id = p.version_member_role_id
                WHERE r.member_type = :type AND p.permission = :permission""")
                .setParameter("type", memberType).setParameter("permission", permission)
                .getSingleResult()).longValue();
    }

    private long countAllPermissionRows() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM version_member_role_permission")
                .getSingleResult()).longValue();
    }

    private long countEditorRoles() {
        return ((Number) em.createNativeQuery("SELECT count(*) FROM version_member_role WHERE member_type = 'EDITOR'")
                .getSingleResult()).longValue();
    }

    /**
     * The project matrix loads members, users, roles and permissions in a
     * constant number of statements: adding members and roles must not add
     * queries (no N+1 on roles or on their permissions).
     */
    @Test
    void projectMatrixQueryCountDoesNotGrowWithMembersAndRoles() {
        org.hibernate.stat.Statistics stats = em.getEntityManagerFactory()
                .unwrap(org.hibernate.SessionFactory.class).getStatistics();

        long small = statementsToLoadMatrix(stats);
        for (int i = 0; i < 6; i++) {
            User extra = user();
            joinProject(extra);
            grant(extra, i % 2 == 0 ? first : second,
                    role(VersionMemberType.EDITOR, VerticalEnum.EXPORT), role(VersionMemberType.SIMULATOR, null));
        }
        em.flush();
        long large = statementsToLoadMatrix(stats);

        assertTrue(small <= 3, "expected at most 3 statements, got " + small);
        assertEquals(small, large, "the statement count must not depend on how many members or roles exist");
    }

    private long statementsToLoadMatrix(org.hibernate.stat.Statistics stats) {
        em.flush();
        em.clear();
        stats.clear();
        List<VersionMember> members = repository.findByProjectIdWithUser(project.getId());
        members.forEach(member -> member.getRoles().forEach(role -> role.getPermissions().size()));
        assertTrue(!members.isEmpty());
        return stats.getPrepareStatementCount();
    }

    private User user() {
        User user = new User("version-member-" + UUID.randomUUID() + "@example.com", "Version", "Member", "hashed");
        em.persist(user);
        return user;
    }

    private void joinProject(User user) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(ProjectMemberType.EDITOR.name(), ProjectMemberType.EDITOR,
                EnumSet.copyOf(ProjectMember.defaultPermissionsFor(ProjectMemberType.EDITOR))));
        project.addMember(member);
        em.persist(member);
    }

    private VersionMemberRole role(VersionMemberType type, VerticalEnum vertical) {
        return new VersionMemberRole(type.name(), type, EnumSet.copyOf(VersionMember.defaultPermissionsFor(type)), vertical);
    }

    private void grant(User user, Version version, VersionMemberRole... roles) {
        VersionMember member = new VersionMember(user, version);
        for (VersionMemberRole role : roles) {
            member.addRole(role);
        }
        em.persist(member);
    }

    private void authenticate(User user) {
        User claims = User.fromJwtClaims(user.getId(), user.getMail(), user.getFirstName(), user.getLastName(), PlatformRole.USER);
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(claims.getId(), null, List.of());
        token.setDetails(new Session("test", claims, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
