package org.enerscope.version.controller;

import jakarta.persistence.EntityManager;
import org.enerscope.economic.service.EconomicExample;
import org.enerscope.jwt.JwtService;
import org.enerscope.money.MoneyAmount;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.GraphPosition;
import org.enerscope.node.model.InvestmentCost;
import org.enerscope.node.model.NodeGraphData;
import org.enerscope.node.model.NodeTypeData;
import org.enerscope.node.model.enums.NodeTypeEnum;
import org.enerscope.node.model.enums.StructuralRoleEnum;
import org.enerscope.node.model.enums.VerticalEnum;
import org.enerscope.node.model.extraction.Well;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.probabilistic.ConstantValue;
import org.enerscope.project.model.Project;
import org.enerscope.project.model.ProjectMember;
import org.enerscope.project.model.ProjectMemberRole;
import org.enerscope.project.model.enums.ProjectMemberType;
import org.enerscope.user.model.User;
import org.enerscope.user.model.enums.PlatformRole;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.model.enums.VersionMemberType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccessHttpFixture {

    private final EntityManager em;
    private final JwtService jwt;
    private final Organization organization;
    private final Project project;
    private final Version version;
    private final List<BaseNode> nodes = new ArrayList<>();
    private final Map<String, String> remappedNodeIds = new HashMap<>();

    public AccessHttpFixture(EntityManager em, JwtService jwt) {
        this.em = em;
        this.jwt = jwt;
        for (BaseNode original : EconomicExample.version().getNodeSnapshot()) {
            Well well = (Well) original;
            String oldId = well.getId().toString();
            ReflectionTestUtils.setField(well, "id", null);
            well.setType(new NodeTypeData(VerticalEnum.EXTRACTION, StructuralRoleEnum.GENERATOR, NodeTypeEnum.WELL));
            well.setIdentityId(UUID.randomUUID());
            well.setGraphData(new NodeGraphData(new GraphPosition(0d, 0d), null));
            well.setInvestmentCost(new InvestmentCost(new ArrayList<>()));
            well.setUpkeepCosts(MoneyAmount.of(2));
            well.setOperatingCosts(MoneyAmount.of(3));
            well.setDTMCost(MoneyAmount.of(4));
            well.setDeclineCurve(new ConstantValue(0));
            em.persist(well);
            nodes.add(well);
            remappedNodeIds.put(oldId, well.getId().toString());
        }
        organization = new Organization("Access fixture " + UUID.randomUUID());
        em.persist(organization);
        version = new Version("Access fixture", null, new ArrayList<>(nodes), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(version);
        project = new Project("Access fixture", "Access fixture", organization);
        project.addVersion(version);
        em.persist(project);
        em.flush();
    }

    public Project project() {
        return project;
    }

    public Version version() {
        return version;
    }

    public UUID nodeId() {
        return nodes.getFirst().getId();
    }

    public String remapNodeIds(String json) {
        String result = json;
        for (Map.Entry<String, String> entry : remappedNodeIds.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public User user(PlatformRole role) {
        User user = new User("access-" + UUID.randomUUID() + "@example.com", "Access", "User", "hashed", role);
        em.persist(user);
        return user;
    }

    public User organizationMember(OrganizationMemberType type) {
        User user = user(PlatformRole.USER);
        joinOrganization(user, type);
        em.flush();
        return user;
    }

    public User projectMember(ProjectMemberType type) {
        User user = organizationMember(OrganizationMemberType.MEMBER);
        ProjectMember member = new ProjectMember(user, project);
        member.addRole(new ProjectMemberRole(type.name(), type, EnumSet.copyOf(ProjectMember.defaultPermissionsFor(type))));
        project.addMember(member);
        em.persist(member);
        em.flush();
        return user;
    }

    public User projectEditorWithVersionRoles(VersionMemberType... types) {
        User user = projectMember(ProjectMemberType.EDITOR);
        VersionMember member = new VersionMember(user, version);
        for (VersionMemberType type : types) {
            member.addRole(new VersionMemberRole(type.name(), type, EnumSet.copyOf(VersionMember.defaultPermissionsFor(type)),
                    type == VersionMemberType.EDITOR ? VerticalEnum.EXTRACTION : null));
        }
        em.persist(member);
        em.flush();
        return user;
    }

    public User versionEditorOf(VerticalEnum vertical) {
        User user = projectMember(ProjectMemberType.EDITOR);
        VersionMember member = new VersionMember(user, version);
        member.addRole(new VersionMemberRole(VersionMemberType.EDITOR.name(), VersionMemberType.EDITOR,
                EnumSet.copyOf(VersionMember.defaultPermissionsFor(VersionMemberType.EDITOR)), vertical));
        em.persist(member);
        em.flush();
        return user;
    }

    public Version detachedVersion() {
        Version detached = new Version("Detached", null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        em.persist(detached);
        em.flush();
        return detached;
    }

    public String bearer(User user) {
        return "Bearer " + jwt.generateAccessToken(User.fromJwtClaims(user.getId(), user.getMail(),
                user.getFirstName(), user.getLastName(), user.getPlatformRole()));
    }

    private void joinOrganization(User user, OrganizationMemberType type) {
        OrganizationMember member = new OrganizationMember(user, organization);
        member.addRole(new OrganizationMemberRole(type.name(), type,
                EnumSet.copyOf(OrganizationService.defaultPermissionsFor(type))));
        organization.addMember(member);
        em.persist(member);
    }
}
