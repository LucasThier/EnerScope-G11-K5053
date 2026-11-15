package org.enerscope.project.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
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
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "economic.worker.enabled=false"})
@Transactional
class ProjectVersionCreationPostgreSqlIT {

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
    ProjectService projectService;

    private Project project;
    private Version parent;
    private User creator;
    private User colleague;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization("Version creation IT " + UUID.randomUUID());
        em.persist(organization);
        parent = new Version("Parent", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(parent);
        project = new Project("Version creation IT", "Disposable fixture", organization);
        project.addVersion(parent);
        em.persist(project);
        creator = user();
        colleague = user();
        joinProject(creator, ProjectMemberType.EDITOR);
        joinProject(colleague, ProjectMemberType.EDITOR);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void theChildCopiesEveryRoleOfTheParentWithItsVerticalNameAndPermissions() {
        User exporter = user();
        joinProject(exporter, ProjectMemberType.EDITOR);
        grant(exporter, parent, role(VersionMemberType.EDITOR, VerticalEnum.EXPORT), role(VersionMemberType.SIMULATOR, null));
        grant(colleague, parent, role(VersionMemberType.ADMIN, null));
        flushAndClear();

        UUID child = createChildAs(user(PlatformRole.ADMIN));

        assertEquals(2, rowsOf(child).size());
        assertEquals(signaturesOf(parent.getId(), exporter), signaturesOf(child, exporter));
        assertEquals(signaturesOf(parent.getId(), colleague), signaturesOf(child, colleague));
        assertEquals(2, signaturesOf(child, exporter).size());
        assertEquals(2, rowsOf(parent.getId()).size());
    }

    @Test
    void theChildDoesNotCopyTheRowOfSomeoneWhoIsNotInTheProjectButKeepsThatOfADeactivatedMember() {
        User outsider = user();
        grant(outsider, parent, role(VersionMemberType.ADMIN, null));
        grant(colleague, parent, role(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION));
        flushAndClear();
        membershipOf(colleague).deactivate();
        flushAndClear();

        UUID child = createChildAs(user(PlatformRole.ADMIN));

        assertTrue(rowsOf(child).stream().noneMatch(row -> row.getUser().getId().equals(outsider.getId())));
        assertEquals(signaturesOf(parent.getId(), colleague), signaturesOf(child, colleague));
    }

    @Test
    void aCreatorWithoutRolesInTheParentBecomesTheOnlyAdminRowOfTheChild() {
        flushAndClear();

        UUID child = createChildAs(creator);

        List<VersionMember> rows = rowsOf(child);
        assertEquals(1, rows.size());
        assertEquals(creator.getId(), rows.get(0).getUser().getId());
        assertEquals(Set.of(adminSignature()), signaturesOf(child, creator));
        assertTrue(rowsOf(parent.getId()).isEmpty());
        assertEquals(1, rowCount(child, creator));
    }

    @Test
    void theCreatorGetsAdminOnTheSameRowCopiedFromTheParent() {
        grant(creator, parent, role(VersionMemberType.EDITOR, VerticalEnum.EXTRACTION));
        grant(colleague, parent, role(VersionMemberType.SIMULATOR, null));
        flushAndClear();
        Set<String> inParentBefore = signaturesOf(parent.getId(), creator);

        UUID child = createChildAs(creator);

        assertEquals(1, rowCount(child, creator));
        assertEquals(1, rowCount(child, colleague));
        Set<String> creatorRoles = signaturesOf(child, creator);
        assertEquals(2, creatorRoles.size());
        assertTrue(creatorRoles.containsAll(inParentBefore));
        assertTrue(creatorRoles.contains(adminSignature()));
        assertEquals(inParentBefore, signaturesOf(parent.getId(), creator));
        assertEquals(signaturesOf(parent.getId(), colleague), signaturesOf(child, colleague));
    }

    @Test
    void aCreatorWhoIsAlreadyAdminOfTheParentKeepsASingleAdminRole() {
        grant(creator, parent, role(VersionMemberType.ADMIN, null));
        flushAndClear();

        UUID child = createChildAs(creator);

        assertEquals(1, rowCount(child, creator));
        assertEquals(Set.of(adminSignature()), signaturesOf(child, creator));
        assertEquals(1, rowsOf(child).get(0).getRoles().size());
    }

    @Test
    void theUniqueConstraintRejectsASecondRowForTheSameUserAndVersion() {
        flushAndClear();
        UUID child = createChildAs(creator);
        flushAndClear();
        assertEquals(1, rowCount(child, creator));

        VersionMember duplicate = new VersionMember(em.find(User.class, creator.getId()), em.find(Version.class, child));
        duplicate.addRole(role(VersionMemberType.SIMULATOR, null));
        em.persist(duplicate);

        assertThrows(PersistenceException.class, () -> em.flush());
    }

    @Test
    void aProjectAdminOrAPlatformAdminCreatingAChildGetsNoRow() {
        User projectAdmin = user();
        joinProject(projectAdmin, ProjectMemberType.ADMIN);
        flushAndClear();

        UUID byProjectAdmin = createChildAs(projectAdmin);
        UUID byPlatformAdmin = createChildAs(user(PlatformRole.ADMIN));

        assertTrue(rowsOf(byProjectAdmin).isEmpty());
        assertTrue(rowsOf(byPlatformAdmin).isEmpty());
    }

    @Test
    void aCreatorWhoseProjectMembershipIsInactiveIsRefusedAndNothingIsCreated() {
        flushAndClear();
        membershipOf(creator).deactivate();
        flushAndClear();
        long before = versionCount();

        assertThrows(ForbiddenException.class, () -> createChildAs(creator));

        assertEquals(before, versionCount());
    }

    private UUID createChildAs(User user) {
        authenticate(user);
        UUID id = projectService.saveVersion(project.getId(), new VersionDTO("Child " + UUID.randomUUID(), parent.getId())).getId();
        flushAndClear();
        return id;
    }

    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private List<VersionMember> rowsOf(UUID version) {
        return em.createQuery("select m from VersionMember m where m.version.id = :version", VersionMember.class)
                .setParameter("version", version)
                .getResultList();
    }

    private long rowCount(UUID version, User user) {
        return ((Number) em.createNativeQuery("select count(*) from version_member where version_id = :version and user_id = :user")
                .setParameter("version", version)
                .setParameter("user", user.getId())
                .getSingleResult()).longValue();
    }

    private long versionCount() {
        return em.createQuery("select count(v) from Version v", Long.class).getSingleResult();
    }

    private Set<String> signaturesOf(UUID version, User user) {
        return rowsOf(version).stream()
                .filter(row -> row.getUser().getId().equals(user.getId()))
                .flatMap(row -> row.getRoles().stream())
                .map(ProjectVersionCreationPostgreSqlIT::signature)
                .collect(Collectors.toSet());
    }

    private static String signature(VersionMemberRole role) {
        return role.getMemberType() + "/" + role.getVertical() + "/" + role.getName() + "/" + new TreeSet<>(role.getPermissions());
    }

    private static String adminSignature() {
        return VersionMemberType.ADMIN + "/null/" + VersionMemberType.ADMIN + "/"
                + new TreeSet<>(VersionMember.defaultPermissionsFor(VersionMemberType.ADMIN));
    }

    private ProjectMember membershipOf(User user) {
        return em.createQuery("select m from ProjectMember m where m.user.id = :user and m.project.id = :project",
                        ProjectMember.class)
                .setParameter("user", user.getId())
                .setParameter("project", project.getId())
                .getSingleResult();
    }

    private User user() {
        User user = new User("version-creation-" + UUID.randomUUID() + "@example.com", "Version", "Creation", "hashed");
        em.persist(user);
        return user;
    }

    private User user(PlatformRole platformRole) {
        User user = new User("version-creation-" + UUID.randomUUID() + "@example.com", "Version", "Creation", "hashed", platformRole);
        em.persist(user);
        return user;
    }

    private void joinProject(User user, ProjectMemberType type) {
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(type.name(), type, EnumSet.copyOf(ProjectMember.defaultPermissionsFor(type))));
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
        User claims = User.fromJwtClaims(user.getId(), user.getMail(), user.getFirstName(), user.getLastName(), user.getPlatformRole());
        UsernamePasswordAuthenticationToken token = new UsernamePasswordAuthenticationToken(claims.getId(), null, List.of());
        token.setDetails(new Session("test", claims, Instant.now().plusSeconds(3600)));
        SecurityContextHolder.getContext().setAuthentication(token);
    }
}
