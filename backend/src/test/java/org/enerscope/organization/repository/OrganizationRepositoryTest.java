package org.enerscope.organization.repository;

import org.enerscope.organization.dto.OrganizationDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class OrganizationRepositoryTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User jane;
    private User joe;

    @BeforeEach
    void setUp() {
        jane = entityManager.persist(
                new User("orgs-jane@enerscope.org", "Jane", "Doe", "hashed", PlatformRole.USER));
        joe = entityManager.persist(
                new User("orgs-joe@enerscope.org", "Joe", "Roe", "hashed", PlatformRole.USER));
    }

    private Organization organization(String name) {
        return entityManager.persist(new Organization(name));
    }

    private void join(User user, Organization organization) {
        OrganizationMember member = new OrganizationMember(user, organization);
        organization.addMember(member);
        entityManager.persist(member);
    }

    private void sync() {
        entityManager.flush();
        entityManager.clear();
    }

    private OrganizationDTO byName(List<OrganizationDTO> rows, String name) {
        return rows.stream().filter(row -> row.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void summariesCountTheMembersOfEachOrganization() {
        Organization acme = organization("Acme");
        join(jane, acme);
        join(joe, acme);
        organization("Globex");
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummaries();

        assertEquals(2L, byName(rows, "Acme").memberCount());
        assertEquals(0L, byName(rows, "Globex").memberCount());
    }

    @Test
    void summariesDoNotCountAnotherOrganizationsMembers() {
        Organization acme = organization("Acme");
        Organization globex = organization("Globex");
        join(jane, acme);
        join(joe, globex);
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummaries();

        assertEquals(1L, byName(rows, "Acme").memberCount());
        assertEquals(1L, byName(rows, "Globex").memberCount());
    }

    @Test
    void summariesComeBackOrderedByName() {
        organization("Zenith");
        organization("Acme");
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummaries();

        assertEquals("Acme", rows.get(0).name());
        assertEquals("Zenith", rows.get(rows.size() - 1).name());
    }

    @Test
    void memberSummariesReturnOnlyTheOrganizationsTheUserBelongsTo() {
        Organization acme = organization("Acme");
        Organization globex = organization("Globex");
        join(jane, acme);
        join(joe, globex);
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummariesForMember(jane.getId());

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).name());
    }

    @Test
    void memberSummariesStillCarryTheFullMemberCount() {
        Organization acme = organization("Acme");
        join(jane, acme);
        join(joe, acme);
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummariesForMember(jane.getId());

        assertEquals(1, rows.size());
        assertEquals(2L, rows.get(0).memberCount());
    }

    @Test
    void memberSummariesAreEmptyForAUserInNoOrganization() {
        join(joe, organization("Globex"));
        sync();

        assertTrue(organizationRepository.findSummariesForMember(jane.getId()).isEmpty());
    }
    @Test
    void summariesIncludeDeactivatedOrganizations() {
        organization("Acme");
        Organization suspended = organization("Zenith");
        suspended.deactivate();
        sync();

        List<OrganizationDTO> rows = organizationRepository.findSummaries();

        assertEquals(2, rows.size());
        assertTrue(rows.stream().anyMatch(row -> !row.active()));
    }

    @Test
    void memberSummariesExcludeDeactivatedOrganizations() {
        Organization acme = organization("Acme");
        join(jane, acme);
        acme.deactivate();
        sync();

        assertTrue(organizationRepository.findSummariesForMember(jane.getId()).isEmpty());
    }

    @Test
    void existsByIdAndActiveTrueIsFalseOnceDeactivated() {
        Organization acme = organization("Acme");
        acme.deactivate();
        sync();

        assertFalse(organizationRepository.existsByIdAndActiveTrue(acme.getId()));
    }

    @Test
    void existsByIdAndActiveTrueIsTrueWhileActive() {
        Organization acme = organization("Acme");
        sync();

        assertTrue(organizationRepository.existsByIdAndActiveTrue(acme.getId()));
    }

    @Test
    void findByIdAndActiveTrueSkipsADeactivatedOrganization() {
        Organization active = organization("Acme");
        Organization suspended = organization("Zenith");
        suspended.deactivate();
        sync();

        assertTrue(organizationRepository.findByIdAndActiveTrue(active.getId()).isPresent());
        assertTrue(organizationRepository.findByIdAndActiveTrue(suspended.getId()).isEmpty());
    }

    private void joinAs(User user, Organization organization, OrganizationMemberType type) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(type.name(), type,
                type == OrganizationMemberType.OWNER
                        ? EnumSet.of(OrganizationMemberPermission.MANAGE_ORGANIZATION,
                                OrganizationMemberPermission.VIEW_ORGANIZATION)
                        : EnumSet.of(OrganizationMemberPermission.VIEW_ORGANIZATION)));
        organization.addMember(member);
        entityManager.persist(member);
    }

    private static final OrganizationMemberPermission MANAGE =
            OrganizationMemberPermission.MANAGE_ORGANIZATION;

    @Test
    void ownedReturnsTheOrganizationsWhereTheCallerHoldsManage() {
        Organization acme = organization("Acme");
        joinAs(jane, acme, OrganizationMemberType.OWNER);
        sync();

        List<OrganizationDTO> rows = organizationRepository.findOwnedBy(jane.getId(), MANAGE);

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).name());
    }

    @Test
    void ownedExcludesOrganizationsWhereTheCallerIsOnlyAMember() {
        Organization acme = organization("Acme");
        joinAs(jane, acme, OrganizationMemberType.MEMBER);
        sync();

        assertTrue(organizationRepository.findOwnedBy(jane.getId(), MANAGE).isEmpty());
    }

    @Test
    void ownedExcludesDeactivatedOrganizations() {
        Organization acme = organization("Acme");
        joinAs(jane, acme, OrganizationMemberType.OWNER);
        acme.deactivate();
        sync();

        assertTrue(organizationRepository.findOwnedBy(jane.getId(), MANAGE).isEmpty());
    }

    @Test
    void ownedExcludesOrganizationsOwnedBySomebodyElse() {
        Organization acme = organization("Acme");
        Organization globex = organization("Globex");
        joinAs(jane, acme, OrganizationMemberType.OWNER);
        joinAs(joe, globex, OrganizationMemberType.OWNER);
        sync();

        List<OrganizationDTO> rows = organizationRepository.findOwnedBy(jane.getId(), MANAGE);

        assertEquals(1, rows.size());
        assertEquals("Acme", rows.get(0).name());
    }

    @Test
    void ownsAnyActiveOrganizationFollowsTheSameRule() {
        Organization acme = organization("Acme");
        joinAs(jane, acme, OrganizationMemberType.OWNER);
        joinAs(joe, acme, OrganizationMemberType.MEMBER);
        sync();

        assertTrue(organizationRepository.ownsAnyActiveOrganization(jane.getId(), MANAGE));
        assertFalse(organizationRepository.ownsAnyActiveOrganization(joe.getId(), MANAGE));
    }

}
