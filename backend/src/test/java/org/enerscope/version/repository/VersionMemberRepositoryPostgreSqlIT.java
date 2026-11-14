package org.enerscope.version.repository;

import jakarta.persistence.EntityManager;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "economic.worker.enabled=false"})
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

    private Version version(String name) {
        Version version = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        return version;
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
