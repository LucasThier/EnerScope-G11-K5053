package org.enerscope.organization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.OrganizationDTO;
import org.enerscope.organization.dto.OrganizationMemberDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Organization-management endpoints. These are not under {@code /auth/**}, so
 * they require a valid Bearer token (see {@code SecurityConfig}).
 */
@RestController
@RequestMapping("/organizations")
@Tag(name = "Organizations", description = "Organization management operations")
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping
    @Operation(summary = "Create an organization", description = "Create a new organization.")
    public ResponseEntity<ApiResponse<OrganizationDTO>> createOrganization(
            @Valid @RequestBody CreateOrganizationRequestDTO data) {
        Organization organization = organizationService.createOrganization(data);
        return Responses.created("Organization created", toDTO(organization));
    }

    @PostMapping("/{organizationId}/members")
    @Operation(summary = "Add a member to an organization",
            description = "Add a user to the organization with a given role.")
    public ResponseEntity<ApiResponse<OrganizationMemberDTO>> addMember(
            @PathVariable UUID organizationId,
            @Valid @RequestBody AddOrganizationMemberRequestDTO data) {
        OrganizationMember member = organizationService.addMember(organizationId, data);
        return Responses.created("Member added", toDTO(member));
    }

    private OrganizationDTO toDTO(Organization organization) {
        return new OrganizationDTO(organization.getId(), organization.getName(), organization.getCreatedAt());
    }

    private OrganizationMemberDTO toDTO(OrganizationMember member) {
        OrganizationMemberRole role = member.getRoles().iterator().next();
        return new OrganizationMemberDTO(
                member.getId(),
                member.getUser().getId(),
                member.getUser().getMail(),
                role.getMemberType(),
                role.getPermissions());
    }
}
