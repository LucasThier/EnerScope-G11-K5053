package org.enerscope.project.repository;

import jakarta.persistence.EntityManager;
import org.enerscope.organization.model.Organization;
import org.enerscope.project.model.Project;
import org.enerscope.version.model.Version;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate", "economic.worker.enabled=false"})
@Transactional
class ProjectRepositoryPostgreSqlIT {

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
    ProjectRepository repository;

    private Project project;
    private Project otherProject;
    private Project retiredProject;
    private Version attached;
    private Version retired;
    private Version inOtherProject;
    private Version inRetiredProject;
    private Version detached;

    @BeforeEach
    void setUp() {
        Organization organization = new Organization("Project repository IT " + UUID.randomUUID());
        em.persist(organization);
        attached = version("Attached");
        retired = version("Retired");
        retired.deactivate();
        inOtherProject = version("In other project");
        inRetiredProject = version("In retired project");
        detached = version("Detached");
        project = project(organization, "Project", attached, retired);
        otherProject = project(organization, "Other project", inOtherProject);
        retiredProject = project(organization, "Retired project", inRetiredProject);
        retiredProject.deactivate();
        em.flush();
        em.clear();
    }

    @Test
    void ownerLookupReturnsTheProjectThatHoldsTheVersion() {
        assertEquals(Optional.of(project.getId()), repository.findOwnerIdByVersionId(attached.getId()));
    }

    @Test
    void ownerLookupAnswersWithTheRightProjectWhenSeveralExist() {
        assertEquals(Optional.of(otherProject.getId()), repository.findOwnerIdByVersionId(inOtherProject.getId()));
        assertEquals(Optional.of(project.getId()), repository.findOwnerIdByVersionId(retired.getId()));
    }

    @Test
    void ownerLookupIsEmptyForAVersionAttachedToNoProject() {
        assertTrue(repository.findOwnerIdByVersionId(detached.getId()).isEmpty());
    }

    @Test
    void ownerLookupIsEmptyForAnUnknownVersionId() {
        assertTrue(repository.findOwnerIdByVersionId(UUID.randomUUID()).isEmpty());
    }

    @Test
    void ownerLookupStillAnswersForAnInactiveVersionOrAnInactiveProject() {
        assertEquals(Optional.of(project.getId()), repository.findOwnerIdByVersionId(retired.getId()));
        assertEquals(Optional.of(retiredProject.getId()), repository.findOwnerIdByVersionId(inRetiredProject.getId()));
    }

    @Test
    void theActiveOnlyLookupLeavesOutAnInactiveVersionOrAnInactiveProject() {
        assertEquals(Optional.of(project.getId()), repository.findIdByVersionId(attached.getId()));
        assertTrue(repository.findIdByVersionId(retired.getId()).isEmpty());
        assertTrue(repository.findIdByVersionId(inRetiredProject.getId()).isEmpty());
        assertTrue(repository.findIdByVersionId(detached.getId()).isEmpty());
    }

    private Version version(String name) {
        Version version = new Version(name, null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        return version;
    }

    private Project project(Organization organization, String name, Version... versions) {
        Project created = new Project(name, "Disposable fixture", organization);
        for (Version version : versions) {
            created.addVersion(version);
        }
        em.persist(created);
        return created;
    }
}
