# Version Merge Conflict Detection Plan

## Overview

This plan outlines the implementation of version merge conflict detection for the EnerScope backend. When a version (branch) is merged with its base, the system will detect conflicts with other derived versions and provide endpoints for conflict resolution.

**Key Clarification**: Version changes are differential to the base - each version stores snapshots and changes relative to its parent, not absolute states.

## Current State Analysis

### Existing Version Structure (Revised Understanding)

From the code review:

- `Version` entity extends `BaseEntity` with:
  - `name`: version name
  - `parentVersion`: reference to parent version (null for base/root)
  - `nodeSnapshot`: **Differential** - Nodes added/removed in this version vs parent (not absolute)
  - `connectionSnapshot`: **Differential** - Connections added/removed in this version vs parent
  - `nodeChanges`: List of NodeChanges tracking modifications in this version vs parent
  - `connectionChanges`: List of ConnectionChanges tracking modifications in this version vs parent
- Change types tracked: ADD, EDIT, DELETE (via `ChangeTypeEnum`)
- When creating a version with a parent, it copies the parent's snapshots as starting point

### How Version State Works

To get the actual state of a version:

1. Start with empty set
2. Walk up the parent chain to the root, accumulating all snapshots and applying changes
3. OR: The version's snapshots + changes represent the delta from parent, so actual state = parent state + version's deltas

## Implementation Plan

### Phase 1: Data Structures & DTOs

#### 1. Conflict Detection DTOs

```java
// ConflictInfo.java - Details about a specific conflict
public class ConflictInfo {
    private UUID entityId; // Node or Connection ID
    private String entityType; // "NODE" or "CONNECTION"
    private String conflictType; // "ADD_ADD", "EDIT_EDIT", "EDIT_DELETE", "DELETE_DELETE"
    private UUID sourceVersionId; // Version being merged (feature branch)
    private UUID targetBaseVersionId; // Target base version (main/trunk)
    private UUID conflictingVersionId; // The derived version that has conflicting changes
    private ChangeTypeEnum sourceChangeType; // Change in feature branch
    private ChangeTypeEnum targetBaseChangeType; // Change in base (from conflicting version's perspective)
    private String description;
    private String sourceEntityDescription; // Human readable description of what's changing
    private String targetEntityDescription; // Human readable description of what's already there
}

// MergeConflictResponse.java - Response for conflict detection
public class MergeConflictResponse {
    private boolean hasConflicts;
    private List<ConflictInfo> conflicts;
    private UUID sourceVersionId; // Version wanting to merge
    private UUID targetBaseVersionId; // Base version to merge into
    private List<UUID> affectedDerivedVersions; // Versions that would be impacted by this merge
}

// MergeOptions.java - Options for resolving conflicts
public class MergeOptions {
    private UUID conflictId; // Index into conflicts array
    private ResolutionStrategy strategy; // How to resolve this specific conflict
    private UUID chosenVersionId; // For MANUAL_MERGE: which version's data to keep as base
}

// ResolutionStrategy.java - Enum for resolution options
public enum ResolutionStrategy {
    ACCEPT_SOURCE,    // Use the source (feature branch) version's changes
    ACCEPT_TARGET,    // Use the target (base) version's changes
    MANUAL_MERGE      // Requires manual intervention - needs chosenVersionId
}
```

#### 2. Enhanced VersionService Methods

```java
// Detect conflicts between merging version and its base
public MergeConflictResponse detectMergeConflicts(UUID featureVersionId, UUID baseVersionId);

// Get all versions derived from base (transitive closure)
public List<Version> getAllDerivedVersions(UUID baseVersionId);

// Get actual state of a version (for comparison)
public VersionState getVersionState(UUID versionId);

// Perform the actual merge with conflict resolution
public Version performMerge(UUID featureVersionId, UUID baseVersionId, List<MergeOptions> resolutionOptions);

// Helper: Reconstruct entity state at a specific version
public BaseNode reconstructNodeState(UUID nodeId, UUID versionId);
public NodeConnection reconstructConnectionState(UUID connectionId, UUID versionId);
```

### Phase 2: Conflict Detection Logic (Revised)

#### Core Understanding

Since versions store deltas:

- To check if version V has changed entity E: look in V's nodeChanges/connectionChanges AND check if E is in V's snapshots
- To get E's state in version V: need to reconstruct by walking up parent chain
- Conflict occurs when: Feature version F and some derived version D both have changes to same entity E relative to base B, and those changes are incompatible

#### Detection Algorithm

To detect conflicts when merging feature version F into base version B:

1. Find all versions derived from B (call this set D) - these are the versions that could be affected
2. For each version D in D:
   - For each entity (node/connection) that F has changed relative to B:
     - Check if D also has changed that same entity relative to B
     - If yes, determine conflict type based on change types
     - If conflict exists, create ConflictInfo
3. Return all conflicts found

#### Change Type Determination Logic

For a given version V and entity E:

- If E is in V's nodeSnapshot/connectionSnapshot AND there's an ADD change: likely the ADD is in the snapshot
- If there's a DELETE change: E was removed in this version
- If there's an EDIT change: E was modified in this version
- Need to check both snapshots and change lists to determine net effect

### Phase 3: Controller Endpoints

Add to VersionController:

```java
// Detect conflicts before merge
@GetMapping(value = "/{featureVersionId}/merge-conflicts/{baseVersionId}")
public ResponseEntity<ApiResponse<MergeConflictResponse>> detectMergeConflicts(
        @PathVariable UUID featureVersionId,
        @PathVariable UUID baseVersionId) {
    MergeConflictResponse response = versionService.detectMergeConflicts(featureVersionId, baseVersionId);
    return Responses.ok("Merge conflicts detected", response);
}

// Get all derived versions that would be affected
@GetMapping(value = "/{baseVersionId}/derived-versions")
public ResponseEntity<ApiResponse<List<Version>>> getAllDerivedVersions(
        @PathVariable UUID baseVersionId) {
    List<Version> versions = versionService.getAllDerivedVersions(baseVersionId);
    return Responses.ok("All derived versions retrieved", versions);
}

// Perform merge with conflict resolution
@PostMapping(value = "/{featureVersionId}/merge/{baseVersionId}")
public ResponseEntity<ApiResponse<Version>> performMerge(
        @PathVariable UUID featureVersionId,
        @PathVariable UUID baseVersionId,
        @RequestBody List<MergeOptions> resolutionOptions) {
    Version mergedVersion = versionService.performMerge(featureVersionId, baseVersionId, resolutionOptions);
    return Responses.ok("Versions merged successfully", mergedVersion);
}

// Get version state for debugging/visualization
@GetMapping(value = "/{versionId}/state")
public ResponseEntity<ApiResponse<VersionState>> getVersionState(
        @PathVariable UUID versionId) {
    VersionState state = versionService.getVersionState(versionId);
    return Responses.ok("Version state retrieved", state);
}
```

### Phase 4: Helper Methods for State Reconstruction

Critical for proper conflict detection:

- `getVersionState(UUID versionId)`: Reconstructs complete state by walking up parent chain
- `reconstructNodeState(UUID nodeId, UUID versionId)`: Gets state of specific node at specific version
- `reconstructConnectionState(UUID connectionId, UUID versionId)`: Gets state of specific connection

These methods will:

1. Start with the target version
2. Walk up parentVersion chain to root
3. Apply all snapshots and changes in order from root to target
4. Return the reconstructed state

### Phase 5: Service Implementation

#### detectMergeConflicts(UUID featureVersionId, UUID baseVersionId)

1. Validate that baseVersionId is actually an ancestor of featureVersionId
2. Get all derived versions of baseVersionId (transitive children)
3. For each derived version DV:
   - For each entity that feature version has changed relative to base:
     - Check if DV also changed that entity relative to base
     - If yes, determine the nature of both changes
     - Classify as conflict if incompatible (ADD-ADD, EDIT-EDIT, EDIT-DELETE, etc.)
     - Create ConflictInfo with detailed descriptions
4. Return response

#### getVersionState(UUID versionId)

1. If versionId is null, return empty state
2. Recursively get parent state
3. Apply this version's snapshots (add nodes/connections in addSnapshot, remove in removeSnapshot)
4. Apply this version's changes (edits modify existing entities)
5. Return combined state

### Phase 6: Testing Strategy

#### Unit Tests

- Test state reconstruction with various version hierarchies
- Test conflict detection: ADD-ADD, EDIT-EDIT, EDIT-DELETE scenarios
- Test no-conflict cases
- Test complex change sequences
- Test edge cases: root versions, linear chains, branching

#### Integration Tests

- Full merge workflow via endpoints
- Multiple derived versions affected
- Persistence of merge results

### Implementation Order

1. Create DTO classes
2. Implement state reconstruction helpers
3. Implement conflict detection logic
4. Implement merge execution logic
5. Add controller endpoints
6. Add comprehensive tests

## Key Technical Details

### Snapshot Interpretation

Looking at VersionService.saveVersion():

```java
connectionSnapshot = parentVersion.getConnectionSnapshot() == null
        ? null
        : new ArrayList<>(parentVersion.getConnectionSnapshot());
nodeSnapshot = parentVersion.getNodeSnapshot() == null
        ? null
        : new ArrayList<>(parentVersion.getNodeSnapshot());
```

This shows snapshots start as copies of parent's snapshots, then changes are applied via:

- `version.getNodeSnapshot().add(savedNode);` (for adds)
- `version.getNodeSnapshot().remove(originalNode);` (for deletes in editNodeInVersion when no prior changes)

So snapshots represent: "What nodes/connections exist in this version THAT DIDN'T EXIST IN THE PARENT" (for adds) and removals are handled by actually removing from the copied snapshot.

Therefore, to get what EXISTS in a version:

1. Start with parent's actual state (recursively)
2. Add nodes in nodeSnapshot
3. Remove nodes that are in nodeSnapshot but were removed via changes? No - looking more carefully...

Actually, looking at editNodeInVersion:

- When node came from parent (no prior ADD): remove from snapshot, add edited version
- When node was added in this version: just edit in place
- When node was edited before: just edit again

And deleteNodeFromVersion:

- Always removes from snapshot
- For nodes added in this version: also deletes the entity
- For nodes from parent: just removes from snapshot (implies it existed in parent)

This is complex. Let me think of a cleaner interpretation:

**Alternative interpretation**: Snapshots represent the CURRENT STATE of nodes/connections that belong specifically to this version (not inherited). Changes represent the HISTORY of how we got there.

But that doesn't fit with the copy-on-create pattern.

Let me look at how VersionService.addNodeToVersion works:

```java
version.getNodeSnapshot().add(savedNode); // Adds to snapshot
NodeChange nodeChange = new NodeChange();
nodeChange.setChangeType(ChangeTypeEnum.ADD);
// ...
version.getNodeChanges().add(nodeChange); // Also adds to changes
```

So both snapshot AND changes get updated for ADD operations.

For EDIT when no prior changes:

```java
version.getNodeSnapshot().remove(originalNode); // Remove from snapshot
version.getNodeSnapshot().add(editedNode); // Add edited version
version.getNodeChanges().add(editChange); // Add to changes
```

So snapshot always contains the current state of version-specific entities, and changes represent the mutation history.

**To get actual state of a version**:

1. Get parent's actual state (recursively)
2. Add all entities in version's nodeSnapshot (these are version-specific additions)
3. Remove all entities that were deleted (tracked via DELETE changes where the entity came from parent)
4. Apply all EDIT changes (modify existing entities)

Actually, even simpler: The snapshots + the version's own changes, when applied to parent state, should give the version state.

Since we copy parent's snapshots on version creation, then apply changes to those copies, the version's snapshots DO represent part of its current state.

Let me trace through addNodeToVersion:

1. Copy parent's snapshots to new version
2. Add new node to version's snapshot
3. Add ADD change to version's changes
   Result: Node exists in version's snapshot and has ADD edit

editNodeInVersion when no prior changes:

1. Copy parent's snapshots
2. Remove original node from version's snapshot
3. Add edited node to version's snapshot
4. Add EDIT change to version's changes
   Result: Edited node exists in version's snapshot, has EDIT change

So **the version's snapshot always contains the current state of entities that exist in this version**, and the changes array represents the historical edits that got us there (for audit/tracking).

This makes conflict detection easier:

- To see what entities exist in version V: look at V's nodeSnapshot/connectionSnapshot
- To see what CHANGES happened in version V: look at V's nodeChanges/connectionChanges
- To see if version V CHANGED entity E: look for E in V's change lists
- To see what the current state of E is in version V: look for E in V's snapshots

**Conflict detection simplified**:
When merging feature branch F into base B:

1. Get F's snapshot (current state of F)
2. Get B's snapshot (current state of B)
3. Find all versions derived from B (potential conflicts)
4. For each derived version D:
   - Get D's snapshot
   - Compare F's snapshot vs B's snapshot to see what F changed
   - Compare D's snapshot vs B's snapshot to see what D changed
   - If same entity changed in both, and changes are incompatible -> conflict

What constitutes a change?

- Entity in F's snapshot but not in B's snapshot -> ADD in F
- Entity in B's snapshot but not in F's snapshot -> DELETE in F
- Entity in both snapshots but different -> EDIT in F

Same for D vs B.

Conflict types:

- ADD-F + ADD-D: Both added (conflict if same logical entity)
- EDIT-F + EDIT-D: Both edited (conflict)
- ADD-F + DELETE-D: F added, D deleted (conflict)
- DELETE-F + ADD-D: F deleted, D added (conflict)
- DELETE-F + DELETE-D: Both deleted (usually not conflict)
- ADD-F + NO-CHANGE-D: No conflict
- etc.

We'll need a way to determine if two entities are the "same logical entity" - probably by some business key or by checking if they represent the same real-world thing.

For now, we'll assume entity ID correspondence means same entity, acknowledging this is a simplification.

### Phase 4: Updated Service Methods

```java
// Get what changed in a version relative to its parent
public EntityChangeSet getChangesRelativeToParent(UUID versionId);
// Returns what was added, removed, modified

// Get actual state of version (for comparison)
public VersionState getVersionState(UUID versionId);

// Detect conflicts
public MergeConflictResponse detectMergeConflicts(UUID featureVersionId, UUID baseVersionId);

// Perform merge
public Version performMerge(UUID featureVersionId, UUID baseVersionId, List<MergeOptions> resolutionOptions);
```

Where:

```java
public class EntityChangeSet {
    private Set<UUID> addedNodes;
    private Set<UUID> removedNodes;
    private Set<UUID> editedNodes;
    // Similar for connections
}
```

## Summary of Approach

1. Leverage existing snapshot storage to determine current state
2. Use change lists for audit/history but detect changes by comparing snapshots
3. For merge conflict detection: compare feature version's changes vs base with each derived version's changes vs base
4. Resolution strategies allow choosing which version's changes to keep
