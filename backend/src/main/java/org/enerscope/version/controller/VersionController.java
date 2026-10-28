package org.enerscope.version.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.UUID;

import org.enerscope.node.dto.BaseNodeDTO;
import org.enerscope.node.dto.ConnectionDTO;
import org.enerscope.node.dto.DiagramDTO;
import org.enerscope.node.dto.NodeBasicsDTO;
import org.enerscope.node.dto.NodeDetailDTO;
import org.enerscope.node.dto.NodeGraphDataDTO;
import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;
import org.enerscope.user.model.User;
import org.enerscope.util.ApiResponse;
import org.enerscope.util.Responses;
import org.enerscope.version.dto.AddVersionMemberRoleRequestDTO;
import org.enerscope.version.dto.VersionDTO;
import org.enerscope.version.dto.ConflictResolutionType;
import org.enerscope.version.dto.VersionConflictDTO;
import org.enerscope.version.dto.VersionMemberCandidateDTO;
import org.enerscope.version.dto.VersionMemberDTO;
import org.enerscope.version.dto.VersionMemberRoleDTO;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.VersionMember;
import org.enerscope.version.model.VersionMemberRole;
import org.enerscope.version.service.VersionConflictService;
import org.enerscope.version.service.VersionMemberService;
import org.enerscope.version.service.VersionService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/version")
@Tag(name = "Versions", description = "Version management operations")
@AllArgsConstructor
public class VersionController {

    private final VersionService versionService;
    private final VersionConflictService versionConflictService;
    private final VersionMemberService versionMemberService;
    /*
     * create a new version.
     */

    @PostMapping(value = "/createtest", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Create a version detached from any project",
            description = "Creates a Version that hangs off no project. Restricted to platform admins: "
                    + "with no owning project there is no membership that could grant access to it. "
                    + "Versions that belong to a project are created with POST /projects/{id}/version.")
    public ResponseEntity<ApiResponse<Version>> createVersion(
            @RequestBody VersionDTO versionDTO) {
        Version version = versionService.saveOrphanVersion(versionDTO);
        return Responses.ok("Version created successfully", version);
    }

    @DeleteMapping(value = "/{id}")
    @Operation(summary = "Delete a version", description = "Delete an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Void>> deleteVersion(
            @PathVariable UUID id) {
        versionService.deleteVersion(id);
        return Responses.ok("Version deleted successfully");
    }

    @GetMapping(value = "/{versionId}/conflicts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List conflicts of a version", description = "Conflicts recorded automatically when a sibling version was merged into the shared parent")
    public ResponseEntity<ApiResponse<List<VersionConflictDTO>>> getConflicts(
            @PathVariable UUID versionId) {
        return Responses.ok("Conflicts retrieved successfully", versionConflictService.getConflictsForVersion(versionId));
    }

    @GetMapping(value = "/conflicts", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List every conflict", description = "All recorded conflicts across every version, regardless of who they affect. For testing/debugging.")
    public ResponseEntity<ApiResponse<List<VersionConflictDTO>>> getAllConflicts() {
        return Responses.ok("Conflicts retrieved successfully", versionConflictService.getAllConflicts());
    }

    @PatchMapping(value = "/conflicts/{conflictId}/resolve/{decision}")
    @Operation(summary = "Resolve a conflict", description = "Accepts the merged parent's data into the sibling version, or rejects it and records the sibling's divergence")
    public ResponseEntity<ApiResponse<VersionConflictDTO>> resolveConflict(
            @PathVariable UUID conflictId,
            @PathVariable ConflictResolutionType decision) {
        return Responses.ok("Conflict resolved successfully", versionConflictService.resolveConflict(conflictId, decision));
    }

    @GetMapping(value = "/{id}")
    @Operation(summary = "Get a version", description = "Retrieve an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> getVersion(
            @PathVariable UUID id) {
        Version version = versionService.getVersion(id);

        return Responses.ok("Version retrieved successfully", version);
    }

    @PatchMapping(value = "/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Modify a version", description = "Modify an existing Version by its ID.")
    public ResponseEntity<ApiResponse<Version>> modifyVersion(
            @PathVariable UUID id,
            @RequestBody VersionDTO versionDTO) {
        Version version = versionService.modifyVersion(id, versionDTO);

        return Responses.ok("Version modified successfully", version);
    }

    @GetMapping(value = "/{versionId}/diagram")
    @Operation(summary = "Get the diagram of a version", description = "Return the nodes and connections the editor renders for a version.")
    public ResponseEntity<ApiResponse<DiagramDTO>> getDiagram(
            @PathVariable UUID versionId) {
        DiagramDTO diagram = versionService.getDiagram(versionId);
        return Responses.ok("Diagram retrieved successfully", diagram);
    }

    @GetMapping(value = "/{versionId}/node/{nodeId}")
    @Operation(summary = "Get a node's full detail", description = "Return a node's common and type-specific fields, for the editor's edit form.")
    public ResponseEntity<ApiResponse<NodeDetailDTO>> getNodeDetail(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId) {
        NodeDetailDTO detail = versionService.getNodeDetail(versionId, nodeId);
        return Responses.ok("Node detail retrieved successfully", detail);
    }

    @PatchMapping(value = "/{versionId}/node/{nodeId}/position", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a node's position", description = "Presentation-only update of a node's diagram (x/y) and/or geographical (lng/lat) position.")
    public ResponseEntity<ApiResponse<BaseNode>> updateNodePosition(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId,
            @RequestBody NodeGraphDataDTO positionDTO) {
        BaseNode result = versionService.updateNodePosition(versionId, nodeId, positionDTO);
        return Responses.ok("Node position updated successfully", result);
    }

    @PatchMapping(value = "/{versionId}/node/{nodeId}/basics", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Update a node's basic fields", description = "Update a node's name and/or state without resending its type-specific fields.")
    public ResponseEntity<ApiResponse<BaseNode>> updateNodeBasics(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId,
            @RequestBody NodeBasicsDTO basics) {
        BaseNode result = versionService.updateNodeBasics(versionId, nodeId, basics);
        return Responses.ok("Node updated successfully", result);
    }

    @PostMapping(value = "/{versionId}/node", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add node to version", description = "Add a node to a version")
    public ResponseEntity<ApiResponse<BaseNode>> addNodeToVersion(
            @PathVariable UUID versionId,
            @RequestBody BaseNodeDTO nodeDTO) {
        BaseNode result = versionService.addNodeToVersion(versionId, nodeDTO);
        return Responses.ok("Node added to version successfully", result);
    }

    @PostMapping(value = "/{versionId}/connection", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Add connection to version", description = "Add a connection to a version")
    public ResponseEntity<ApiResponse<Object>> addConnectionToVersion(
            @PathVariable UUID versionId,
            @RequestBody ConnectionDTO connectionDTO) {
        Object result = versionService.addConnectionToVersion(versionId, connectionDTO);
        return Responses.ok("Connection added to version successfully", result);
    }

    @PatchMapping(value = "/{versionId}/node/{nodeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit node in version", description = "Edit a node in a version")
    public ResponseEntity<ApiResponse<BaseNode>> editNodeInVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId,
            @RequestBody BaseNodeDTO nodeDTO) {
        BaseNode result = versionService.editNodeInVersion(versionId, nodeId, nodeDTO);
        return Responses.ok("Node edited in version successfully", result);
    }

    @PatchMapping(value = "/{versionId}/connection/{connectionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Edit connection in version", description = "Edit a connection in a version")
    public ResponseEntity<ApiResponse<NodeConnection>> editConnectionInVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID connectionId,
            @RequestBody ConnectionDTO connectionDTO) {
        NodeConnection result = versionService.editConnectionInVersion(versionId, connectionId, connectionDTO);
        return Responses.ok("Connection edited in version successfully", result);
    }

    @DeleteMapping(value = "/{versionId}/node/{nodeId}")
    @Operation(summary = "Delete node from version", description = "Delete a node from a version")
    public ResponseEntity<ApiResponse<Void>> deleteNodeFromVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID nodeId) {
        versionService.deleteNodeFromVersion(versionId, nodeId);
        return Responses.ok("Node deleted from version successfully");
    }

    @DeleteMapping(value = "/{versionId}/connection/{connectionId}")
    @Operation(summary = "Delete connection from version", description = "Delete a connection from a version")
    public ResponseEntity<ApiResponse<Void>> deleteConnectionFromVersion(
            @PathVariable UUID versionId,
            @PathVariable UUID connectionId) {
        versionService.deleteConnectionFromVersion(versionId, connectionId);
        return Responses.ok("Connection deleted from version successfully");
    }

    @PatchMapping(value = "/merge/{subId}")
    @Operation(summary = "Merge subversion into parent version", description = "Merge a subversion into its parent version by replacing snapshots and reconciling changes")
    public ResponseEntity<ApiResponse<Version>> mergeSubVersionIntoParent(
            @PathVariable UUID subId) {
        Version version = versionService.mergeSubVersionIntoParent(subId);
        return Responses.ok("Subversion merged into parent version successfully", version);
    }

    @PostMapping(value = "/{versionId}/members", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Grant a role on a version",
            description = "Grants a user a role on the version: ADMIN (delete/merge/resolve conflicts), "
                    + "EDITOR for one vertical, or SIMULATOR. Adds the role on top of whatever roles the "
                    + "user already has on this version - it never replaces them.")
    public ResponseEntity<ApiResponse<VersionMemberDTO>> addVersionMember(
            @PathVariable UUID versionId,
            @Valid @RequestBody AddVersionMemberRoleRequestDTO data) {
        VersionMember member = versionMemberService.addRole(versionId, data);
        return Responses.created("Member role granted", toDTO(member));
    }

    @GetMapping(value = "/{versionId}/members", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the members of a version", description = "Returns every member with every role they hold on this version.")
    public ResponseEntity<ApiResponse<List<VersionMemberDTO>>> listVersionMembers(
            @PathVariable UUID versionId) {
        List<VersionMemberDTO> members = versionMemberService.listMembers(versionId).stream()
                .map(this::toDTO)
                .toList();
        return Responses.ok("Version members", members);
    }

    @DeleteMapping(value = "/{versionId}/members/{memberId}/roles/{roleId}")
    @Operation(summary = "Revoke a version role",
            description = "Revokes a single role from a member. If it was their only role on this "
                    + "version, the membership itself is removed.")
    public ResponseEntity<ApiResponse<Void>> removeVersionMemberRole(
            @PathVariable UUID versionId,
            @PathVariable UUID memberId,
            @PathVariable UUID roleId) {
        versionMemberService.removeRole(versionId, memberId, roleId);
        return Responses.ok("Role revoked");
    }

    @GetMapping(value = "/{versionId}/member-candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "List the users that can be granted a version role",
            description = "Active members of the version's owning project. The optional q filters by "
                    + "first name, last name or mail.")
    public ResponseEntity<ApiResponse<List<VersionMemberCandidateDTO>>> listVersionMemberCandidates(
            @PathVariable UUID versionId,
            @RequestParam(required = false) String q) {
        return Responses.ok("Member candidates", versionMemberService.listMemberCandidates(versionId, q));
    }

    private VersionMemberDTO toDTO(VersionMember member) {
        User user = member.getUser();
        List<VersionMemberRoleDTO> roles = member.getRoles().stream()
                .map(this::toDTO)
                .toList();
        return new VersionMemberDTO(
                member.getId(),
                user.getId(),
                user.getMail(),
                user.getFirstName(),
                user.getLastName(),
                user.getJobTitle(),
                user.isActive(),
                roles);
    }

    private VersionMemberRoleDTO toDTO(VersionMemberRole role) {
        return new VersionMemberRoleDTO(
                role.getId(), role.getMemberType(), role.getVertical(), role.getPermissions());
    }
}
