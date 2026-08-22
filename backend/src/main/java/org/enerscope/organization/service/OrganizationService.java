package org.enerscope.organization.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.CreateProjectRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.model.Project;
import org.enerscope.organization.model.enums.OrganizationMemberPermission;
import org.enerscope.organization.model.enums.OrganizationMemberType;
import org.enerscope.organization.repository.OrganizationMemberRepository;
import org.enerscope.organization.repository.OrganizationRepository;
import org.enerscope.organization.repository.ProjectRepository;
import org.enerscope.user.model.User;
import org.enerscope.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationService {

    // Default permission set granted per member type when a member is added.
    // There is no API to customize permissions yet; when that becomes needed,
    // extend AddOrganizationMemberRequestDTO instead of this map.
    private static final Map<OrganizationMemberType, Set<OrganizationMemberPermission>> DEFAULT_PERMISSIONS = Map.of(
            OrganizationMemberType.OWNER, EnumSet.of(
                    OrganizationMemberPermission.MANAGE_ORGANIZATION,
                    OrganizationMemberPermission.VIEW_ORGANIZATION),
            OrganizationMemberType.MEMBER, EnumSet.of(
                    OrganizationMemberPermission.VIEW_ORGANIZATION)
    );

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AppLogger logger;

    public OrganizationService(OrganizationRepository organizationRepository,
                                OrganizationMemberRepository organizationMemberRepository,
                                ProjectRepository projectRepository,
                                UserRepository userRepository,
                                AppLogger logger) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.logger = logger;
    }

    public Organization createOrganization(CreateOrganizationRequestDTO data) {
        Organization organization = new Organization(data.name());
        Organization saved = organizationRepository.save(organization);
        logger.info("Created organization {}", saved.getName());
        return saved;
    }

    public OrganizationMember addMember(UUID organizationId, AddOrganizationMemberRequestDTO data) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        User user = userRepository.findById(data.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (organizationMemberRepository.existsByOrganizationIdAndUserId(organizationId, data.userId())) {
            throw new IllegalArgumentException("User is already a member of this organization");
        }

        OrganizationMember member = new OrganizationMember(user, organization);
        OrganizationMemberRole role = new OrganizationMemberRole(
                data.memberType().name(), data.memberType(), DEFAULT_PERMISSIONS.get(data.memberType()));
        member.addRole(role);
        organization.addMember(member);

        OrganizationMember saved = organizationMemberRepository.save(member);
        logger.info("Added user {} to organization {} as {}", user.getMail(), organization.getName(), data.memberType());
        return saved;
    }

    public Project addProject(UUID organizationId, CreateProjectRequestDTO data) {
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found"));

        Project project = new Project(data.name(), data.description(), organization);
        organization.addProject(project);

        Project saved = projectRepository.save(project);
        logger.info("Added project {} to organization {}", saved.getName(), organization.getName());
        return saved;
    }
}
