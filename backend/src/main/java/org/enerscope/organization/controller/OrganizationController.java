package org.enerscope.organization.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.enerscope.organization.dto.AddOrganizationMemberRequestDTO;
import org.enerscope.organization.dto.BulkRegistrationResultDTO;
import org.enerscope.organization.dto.CreateOrganizationRequestDTO;
import org.enerscope.organization.dto.OrganizationDTO;
import org.enerscope.organization.dto.OrganizationMemberDTO;
import org.enerscope.organization.dto.RegisterOrganizationUserRequestDTO;
import org.enerscope.organization.model.Organization;
import org.enerscope.organization.model.OrganizationMember;
import org.enerscope.organization.model.OrganizationMemberRole;
import org.enerscope.organization.service.OrganizationBulkRegistrationService;
import org.enerscope.organization.service.OrganizationService;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
    private final OrganizationBulkRegistrationService bulkRegistrationService;

    public OrganizationController(OrganizationService organizationService,
                                 OrganizationBulkRegistrationService bulkRegistrationService) {
        this.organizationService = organizationService;
        this.bulkRegistrationService = bulkRegistrationService;
    }

    @GetMapping
    @Operation(summary = "List organizations",
            description = "Platform admins get every organization; other users get the ones they belong to.")
    public ResponseEntity<ApiResponse<List<OrganizationDTO>>> listOrganizations() {
        List<OrganizationDTO> organizations = organizationService.listForCurrentUser().stream()
                .map(this::toDTO)
                .toList();
        return Responses.ok("Organizations", organizations);
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

    @PostMapping("/{organizationId}/users")
    @Operation(summary = "Register a new user into an organization",
            description = "Create a brand new platform user and add them to the organization as a "
                    + "MEMBER. Allowed for platform admins and organization owners "
                    + "(members with the MANAGE_ORGANIZATION permission).")
    public ResponseEntity<ApiResponse<OrganizationMemberDTO>> registerUser(
            @PathVariable UUID organizationId,
            @Valid @RequestBody RegisterOrganizationUserRequestDTO data) {
        OrganizationMember member = organizationService.registerUserInOrganization(organizationId, data);
        return Responses.created("User registered into organization", toDTO(member));
    }

    @PostMapping(value = "/{organizationId}/users/bulk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-register users into an organization",
            description = "Upload a CSV with 'mail', 'firstName', 'lastName' (and optional 'role') "
                    + "columns. Each valid row is created with a securely generated password and "
                    + "added to the organization (default MEMBER). Allowed for platform admins and "
                    + "organization owners. The response includes a 'mail,password' CSV to distribute.")
    public ResponseEntity<ApiResponse<BulkRegistrationResultDTO>> bulkRegisterUsers(
            @PathVariable UUID organizationId,
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("The uploaded file is empty");
        }

        String content;
        try {
            content = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Could not read the uploaded file");
        }

        BulkRegistrationResultDTO result = bulkRegistrationService.register(organizationId, content);
        return Responses.ok(
                "Processed " + result.total() + " rows: "
                        + result.created() + " created, " + result.failed() + " failed",
                result);
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
