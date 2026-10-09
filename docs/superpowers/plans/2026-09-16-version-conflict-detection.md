# Version Conflict Detection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement conflict detection after version merge so that child versions can be notified of changes and decide whether to incorporate them.

**Architecture:** 
- Extend the Version entity to track merged parent information
- Add a service method to detect conflicts between a version and its ancestors
- Create REST endpoints to fetch conflicts and mark them as resolved
- Modify the merge operation to record merge history for conflict detection

**Tech Stack:**
- Java Spring Boot
- JPA/Hibernate
- REST API
- Existing Version model and service

## Global Constraints

- Maintain backward compatibility with existing versioning functionality
- Follow existing code patterns in the version module
- All changes must be covered by unit tests
- Use existing logging patterns (AppLogger)
- Keep changes focused on version conflict detection only

---

### Task 1: Extend Version Entity for Merge Tracking

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/model/Version.java`

**Interfaces:**
- Consumes: None
- Produces: Extended Version entity with merge tracking fields

- [ ] **Step 1: Add merge tracking fields to Version entity**

```java
// Add these fields to the Version class:
@ManyToOne
@JoinColumn(name = "merged_from_version_id")
private Version mergedFromVersion;

@Column(name = "merge_timestamp")
private LocalDateTime mergeTimestamp;

// Add getters and setters (Lombok will generate these if we add the annotations properly)
```

- [ ] **Step 2: Run tests to verify entity changes don't break existing functionality**

Run: `mvn test -Dtest=VersionServiceTest`
Expected: All existing tests should pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/model/Version.java
git commit -m "feat: add merge tracking fields to Version entity"
```

### Task 2: Update VersionService to Record Merge Information

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/service/VersionService.java`

**Interfaces:**
- Consumes: Extended Version entity
- Produces: Enhanced merge operation that records merge history

- [ ] **Step 1: Modify mergeSubVersionIntoParent to record merge information**

In the mergeSubVersionIntoParent method, after getting the subVersion and parentVersion but before replacing snapshots, add:

```java
// Record merge information in parent version
parentVersion.setMergedFromVersion(subVersion);
parentVersion.setMergeTimestamp(LocalDateTime.now());
```

- [ ] **Step 2: Add required import for LocalDateTime**

Add at the top of the file:
```java
import java.time.LocalDateTime;
```

- [ ] **Step 3: Run tests to verify merge still works**

Run: `mvn test -Dtest=VersionServiceTest`
Expected: All existing tests should pass (we're not changing behavior, just adding tracking)

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/service/VersionService.java
git commit -m "feat: update merge operation to record merge history"
```

### Task 3: Create Conflict Detection Service

**Files:**
- Create: `backend/src/main/java/org/enerscope/version/service/VersionConflictService.java`

**Interfaces:**
- Consumes: VersionRepository
- Produces: Conflict detection capabilities

- [ ] **Step 1: Create the VersionConflictService class**

```java
package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.version.model.Version;
import org.enerscope.version.repository.VersionRepository;
import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@AllArgsConstructor
@Getter
public class VersionConflictService {

    private final VersionRepository versionRepository;
    private final AppLogger logger;

    /**
     * Detects conflicts between a version and its merged parent's siblings.
     * Returns a list of changes that exist in the merged parent but not in this version.
     */
    public List<VersionConflictDTO> detectConflicts(UUID versionId) {
        Version version = versionRepository.findById(versionId)
                .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));

        // If this version wasn't created from a merge, no conflicts to detect
        if (version.getMergedFromVersion() == null) {
            return new ArrayList<>();
        }

        Version mergedParent = version.getMergedFromVersion();
        if (mergedParent == null) {
            return new ArrayList<>();
        }

        // Get all siblings (other children of the same parent)
        List<Version> siblings = versionRepository.findByParentVersionId(
                mergedParent.getParentVersion() != null ? 
                mergedParent.getParentVersion().getId() : null);

        // Filter to only siblings that were merged after our merge timestamp
        Set<UUID> relevantSiblingIds = new HashSet<>();
        for (Version sibling : siblings) {
            if (sibling.getMergeTimestamp() != null && 
                version.getMergeTimestamp() != null &&
                sibling.getMergeTimestamp().isAfter(version.getMergeTimestamp())) {
                relevantSiblingIds.add(sibling.getId());
            }
        }

        // For each relevant sibling, detect what changes it introduced
        List<VersionConflictDTO> conflicts = new ArrayList<>();
        for (UUID siblingId : relevantSiblingIds) {
            Version sibling = versionRepository.findById(siblingId)
                    .orElseThrow(() -> new IllegalArgumentException("Sibling version not found: " + siblingId));
            
            // Detect node conflicts
            List<NodeConflictDTO> nodeConflicts = detectNodeConflicts(version, sibling);
            // Detect connection conflicts  
            List<ConnectionConflictDTO> connectionConflicts = detectConnectionConflicts(version, sibling);
            
            if (!nodeConflicts.isEmpty() || !connectionConflicts.isEmpty()) {
                conflicts.add(new VersionConflictDTO(
                    sibling.getId(),
                    sibling.getName(),
                    sibling.getMergeTimestamp(),
                    nodeConflicts,
                    connectionConflicts
                ));
            }
        }

        return conflicts;
    }

    private List<NodeConflictDTO> detectNodeConflicts(Version version, Version sibling) {
        List<NodeConflictDTO> conflicts = new ArrayList<>();
        
        // Get nodes that exist in sibling but not in version
        Set<UUID> versionNodeIds = getNodeIds(version.getNodeSnapshot());
        Set<UUID> siblingNodeIds = getNodeIds(sibling.getNodeSnapshot());
        
        siblingNodeIds.removeAll(versionNodeIds); // Keep only nodes in sibling but not in version
        
        for (UUID nodeId : siblingNodeIds) {
            // Find the actual node in sibling's snapshot
            BaseNode node = findNodeById(sibling.getNodeSnapshot(), nodeId);
            if (node != null) {
                conflicts.add(new NodeConflictDTO(
                    nodeId,
                    node.getName(),
                    node.getType().getNodeType(),
                    "ADDED" // Since it's in sibling but not version
                ));
            }
        }
        
        return conflicts;
    }

    private List<ConnectionConflictDTO> detectConnectionConflicts(Version version, Version sibling) {
        List<ConnectionConflictDTO> conflicts = new ArrayList<>();
        
        // Get connections that exist in sibling but not in version
        Set<UUID> versionConnectionIds = getConnectionIds(version.getConnectionSnapshot());
        Set<UUID> siblingConnectionIds = getConnectionIds(sibling.getConnectionSnapshot());
        
        siblingConnectionIds.removeAll(versionConnectionIds); // Keep only connections in sibling but not in version
        
        for (UUID connectionId : siblingConnectionIds) {
            // Find the actual connection in sibling's snapshot
            NodeConnection connection = findConnectionById(sibling.getConnectionSnapshot(), connectionId);
            if (connection != null) {
                conflicts.add(new ConnectionConflictDTO(
                    connectionId,
                    connection.getFromNode().getName() + " -> " + connection.getToNode().getName(),
                    "ADDED" // Since it's in sibling but not version
                ));
            }
        }
        
        return conflicts;
    }

    private Set<UUID> getNodeIds(List<BaseNode> nodes) {
        Set<UUID> ids = new HashSet<>();
        if (nodes != null) {
            for (BaseNode node : nodes) {
                if (node != null && node.getId() != null) {
                    ids.add(node.getId());
                }
            }
        }
        return ids;
    }

    private Set<UUID> getConnectionIds(List<NodeConnection> connections) {
        Set<UUID> ids = new HashSet<>();
        if (connections != null) {
            for (NodeConnection connection : connections) {
                if (connection != null && connection.getId() != null) {
                    ids.add(connection.getId());
                }
            }
        }
        return ids;
    }

    private BaseNode findNodeById(List<BaseNode> nodes, UUID nodeId) {
        if (nodes == null) return null;
        for (BaseNode node : nodes) {
            if (node != null && node.getId() != null && node.getId().equals(nodeId)) {
                return node;
            }
        }
        return null;
    }

    private NodeConnection findConnectionById(List<NodeConnection> connections, UUID connectionId) {
        if (connections == null) return null;
        for (NodeConnection connection : connections) {
            if (connection != null && connection.getId() != null && connection.getId().equals(connectionId)) {
                return connection;
            }
        }
        return null;
    }
}
```

- [ ] **Step 2: Create DTO classes for conflict information**

Create: `backend/src/main/java/org/enerscope/version/dto/VersionConflictDTO.java`
```java
package org.enerscope.version.dto;

import org.enerscope.node.model.BaseNode;
import org.enerscope.node.model.NodeConnection;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public class VersionConflictDTO {
    private UUID versionId;
    private String versionName;
    private LocalDateTime mergeTimestamp;
    private List<NodeConflictDTO> nodeConflicts;
    private List<ConnectionConflictDTO> connectionConflicts;

    public VersionConflictDTO(UUID versionId, String versionName, LocalDateTime mergeTimestamp,
                              List<NodeConflictDTO> nodeConflicts, List<ConnectionConflictDTO> connectionConflicts) {
        this.versionId = versionId;
        this.versionName = versionName;
        this.mergeTimestamp = mergeTimestamp;
        this.nodeConflicts = nodeConflicts;
        this.connectionConflicts = connectionConflicts;
    }

    // Getters and setters
    public UUID getVersionId() { return versionId; }
    public void setVersionId(UUID versionId) { this.versionId = versionId; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public LocalDateTime getMergeTimestamp() { return mergeTimestamp; }
    public void setMergeTimestamp(LocalDateTime mergeTimestamp) { this.mergeTimestamp = mergeTimestamp; }
    public List<NodeConflictDTO> getNodeConflicts() { return nodeConflicts; }
    public void setNodeConflicts(List<NodeConflictDTO> nodeConflicts) { this.nodeConflicts = nodeConflicts; }
    public List<ConnectionConflictDTO> getConnectionConflicts() { return connectionConflicts; }
    public void setConnectionConflicts(List<ConnectionConflictDTO> connectionConflicts) { this.connectionConflicts = connectionConflicts; }
}
```

Create: `backend/src/main/java/org/enerscope/version/dto/NodeConflictDTO.java`
```java
package org.enerscope.version.dto;

import java.util.UUID;

public class NodeConflictDTO {
    private UUID nodeId;
    private String nodeName;
    private String nodeType;
    private String changeType; // ADDED, MODIFIED, etc.

    public NodeConflictDTO(UUID nodeId, String nodeName, String nodeType, String changeType) {
        this.nodeId = nodeId;
        this.nodeName = nodeName;
        this.nodeType = nodeType;
        this.changeType = changeType;
    }

    // Getters and setters
    public UUID getNodeId() { return nodeId; }
    public void setNodeId(UUID nodeId) { this.nodeId = nodeId; }
    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
}
```

Create: `backend/src/main/java/org/enerscope/version/dto/ConnectionConflictDTO.java`
```java
package org.enerscope.version.dto;

import java.util.UUID;

public class ConnectionConflictDTO {
    private UUID connectionId;
    private String connectionDescription;
    private String changeType; // ADDED, MODIFIED, etc.

    public ConnectionConflictDTO(UUID connectionId, String connectionDescription, String changeType) {
        this.connectionId = connectionId;
        this.connectionDescription = connectionDescription;
        this.changeType = changeType;
    }

    // Getters and setters
    public UUID getConnectionId() { return connectionId; }
    public void setConnectionId(UUID connectionId) { this.connectionId = connectionId; }
    public String getConnectionDescription() { return connectionDescription; }
    public void setConnectionDescription(String connectionDescription) { this.connectionDescription = connectionDescription; }
    public String getChangeType() { return changeType; }
    public void setChangeType(String changeType) { this.changeType = changeType; }
}
```

- [ ] **Step 3: Run tests to verify new service compiles**

Run: `mvn compile`
Expected: Compilation should succeed

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/service/VersionConflictService.java
git add backend/src/main/java/org/enerscope/version/dto/VersionConflictDTO.java
git add backend/src/main/java/org/enerscope/version/dto/NodeConflictDTO.java
git add backend/src/main/java/org/enerscope/version/dto/ConnectionConflictDTO.java
git commit -m "feat: create conflict detection service and DTOs"
```

### Task 4: Create Conflict Resolution Tracking

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/model/Version.java`
- Create: `backend/src/main/java/org/enerscope/version/model/VersionConflictResolution.java`

**Interfaces:**
- Consumes: Version entity
- Produces: Ability to track which conflicts have been resolved

- [ ] **Step 1: Add conflict resolution tracking to Version entity**

Add to Version.java:
```java
@OneToMany(mappedBy = "version", cascade = CascadeType.ALL, orphanRemoval = true)
private List<VersionConflictResolution> resolvedConflicts = new ArrayList<>();
```

- [ ] **Step 2: Create VersionConflictResolution entity**

Create: `backend/src/main/java/org/enerscope/version/model/VersionConflictResolution.java`
```java
package org.enerscope.version.model;

import org.enerscope.common.BaseEntity;
import org.enerscope.version.model.Version;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "version_conflict_resolution")
public class VersionConflictResolution extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID conflictingVersionId;

    @Column(nullable = false)
    private LocalDateTime resolutionTimestamp;

    @ManyToOne
    @JoinColumn(name = "version_id", nullable = false)
    private Version version;

    // Constructor for convenience
    public VersionConflictResolution(UUID conflictingVersionId, Version version) {
        this.conflictingVersionId = conflictingVersionId;
        this.version = version;
        this.resolutionTimestamp = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Run tests to verify new entities compile**

Run: `mvn compile`
Expected: Compilation should succeed

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/model/Version.java
git add backend/src/main/java/org/enerscope/version/model/VersionConflictResolution.java
git commit -m "feat: add conflict resolution tracking to Version entity"
```

### Task 5: Create Conflict Resolution Service Methods

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/service/VersionConflictService.java`

**Interfaces:**
- Consumes: VersionConflictService, VersionConflictResolutionRepository
- Produces: Methods to resolve conflicts and get resolution status

- [ ] **Step 1: Add repository dependency and resolution methods to VersionConflictService**

Modify VersionConflictService to add:
```java
private final VersionConflictResolutionRepository conflictResolutionRepository;

// Add constructor parameter
public VersionConflictService(VersionRepository versionRepository, 
                              VersionConflictResolutionRepository conflictResolutionRepository,
                              AppLogger logger) {
    this.versionRepository = versionRepository;
    this.conflictResolutionRepository = conflictResolutionRepository;
    this.logger = logger;
}

// Add method to mark conflicts as resolved
public void resolveConflict(UUID versionId, UUID conflictingVersionId) {
    Version version = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("Version not found: " + versionId));
            
    VersionConflictResolution resolution = new VersionConflictResolution(conflictingVersionId, version);
    conflictResolutionRepository.save(resolution);
    
    logger.info("Resolved conflict for version {} with conflicting version {}", versionId, conflictingVersionId);
}

// Add method to check if conflict is already resolved
public boolean isConflictResolved(UUID versionId, UUID conflictingVersionId) {
    return conflictResolutionRepository.existsByVersionIdAndConflictingVersionId(versionId, conflictingVersionId);
}
```

- [ ] **Step 2: Create VersionConflictResolutionRepository**

Create: `backend/src/main/java/org/enerscope/version/repository/VersionConflictResolutionRepository.java`
```java
package org.enerscope.version.repository;

import org.enerscope.version.model.VersionConflictResolution;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface VersionConflictResolutionRepository extends JpaRepository<VersionConflictResolution, Long> {
    boolean existsByVersionIdAndConflictingVersionId(UUID versionId, UUID conflictingVersionId);
}
```

- [ ] **Step 3: Run tests to verify new service methods compile**

Run: `mvn compile`
Expected: Compilation should succeed

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/service/VersionConflictService.java
git add backend/src/main/java/org/enerscope/version/repository/VersionConflictResolutionRepository.java
git commit -m "feat: add conflict resolution methods and repository"
```

### Task 6: Create REST Endpoints for Conflict Detection

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/controller/VersionController.java`

**Interfaces:**
- Consumes: VersionConflictService
- Produces: REST API for conflict detection and resolution

- [ ] **Step 1: Add service dependency to VersionController**

Add to VersionController constructor and field:
```java
private final VersionConflictService versionConflictService;

// In constructor:
public VersionController(VersionService versionService, VersionConflictService versionConflictService) {
    this.versionService = versionService;
    this.versionConflictService = versionConflictService;
}
```

- [ ] **Step 2: Add endpoint to detect conflicts**

Add to VersionController:
```java
@GetMapping(value = "/{versionId}/conflicts", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = "Detect conflicts for a version", description = "Detects conflicts between a version and its merged parent's siblings")
public ResponseEntity<ApiResponse<List<VersionConflictDTO>>> detectConflicts(
        @PathVariable UUID versionId) {
    List<VersionConflictDTO> conflicts = versionConflictService.detectConflicts(versionId);
    return Responses.ok("Conflicts detected successfully", conflicts);
}
```

- [ ] **Step 3: Add endpoint to resolve conflicts**

Add to VersionController:
```java
@PostMapping(value = "/{versionId}/conflicts/{conflictingVersionId}/resolve", consumes = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = "Resolve a conflict", description = "Marks a conflict as resolved for a version")
public ResponseEntity<ApiResponse<Void>> resolveConflict(
        @PathVariable UUID versionId,
        @PathVariable UUID conflictingVersionId) {
    versionConflictService.resolveConflict(versionId, conflictingVersionId);
    return Responses.ok("Conflict resolved successfully");
}
```

- [ ] **Step 4: Add endpoint to check if conflict is resolved**

Add to VersionController:
```java
@GetMapping(value = "/{versionId}/conflicts/{conflictingVersionId}/resolved", produces = MediaType.APPLICATION_JSON_VALUE)
@Operation(summary = "Check if conflict is resolved", description = "Checks if a conflict has been marked as resolved for a version")
public ResponseEntity<ApiResponse<Boolean>> isConflictResolved(
        @PathVariable UUID versionId,
        @PathVariable UUID conflictingVersionId) {
    boolean resolved = versionConflictService.isConflictResolved(versionId, conflictingVersionId);
    return Responses.ok("Conflict resolution status retrieved", resolved);
}
```

- [ ] **Step 5: Run tests to verify controller compiles**

Run: `mvn compile`
Expected: Compilation should succeed

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/controller/VersionController.java
git commit -m "feat: add REST endpoints for conflict detection and resolution"
```

### Task 7: Create Unit Tests for New Functionality

**Files:**
- Create: `backend/src/test/java/org/enerscope/version/service/VersionConflictServiceTest.java`
- Create: `backend/src/test/java/org/enerscope/version/controller/VersionControllerTest.java` (if not exists, otherwise modify)

**Interfaces:**
- Consumes: New conflict detection service
- Produces: Test coverage for conflict detection functionality

- [ ] **Step 1: Create VersionConflictServiceTest**

Create: `backend/src/test/java/org/enerscope/version/service/VersionConflictServiceTest.java`
```java
package org.enerscope.version.service;

import org.enerscope.logging.AppLogger;
import org.enerscope.version.dto.ConnectionConflictDTO;
import org.enerscope.version.dto.NodeConflictDTO;
import org.enerscope.version.dto.VersionConflictDTO;
import org.enerscope.version.model.BaseNode;
import org.enerscope.version.model.Version;
import org.enerscope.version.model.NodeConnection;
import org.enerscope.version.repository.VersionConflictResolutionRepository;
import org.enerscope.version.repository.VersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class VersionConflictServiceTest {

    @Mock
    private VersionRepository versionRepository;

    @Mock
    private VersionConflictResolutionRepository conflictResolutionRepository;

    @Mock
    private AppLogger logger;

    @InjectMocks
    private VersionConflictService versionConflictService;

    private UUID versionId;
    private UUID parentId;
    private UUID siblingId;
    private UUID conflictingSiblingId;

    @BeforeEach
    void setUp() {
        versionId = UUID.randomUUID();
        parentId = UUID.randomUUID();
        siblingId = UUID.randomUUID();
        conflictingSiblingId = UUID.randomUUID();
        
        // Using mocks for testing
        versionConflictService = new VersionConflictService(
                versionRepository,
                conflictResolutionRepository,
                logger);
    }

    @Test
    void detectConflicts_WhenVersionHasNoMerge_ShouldReturnEmptyList() {
        // Given
        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        version.setId(versionId);
        // mergedFromVersion remains null

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));

        // When
        List<VersionConflictDTO> conflicts = versionConflictService.detectConflicts(versionId);

        // Then
        assertNotNull(conflicts);
        assertTrue(conflicts.isEmpty());
        verify(versionRepository).findById(versionId);
    }

    @Test
    void detectConflicts_WhenVersionHasMergeButNoSiblingChanges_ShouldReturnEmptyList() {
        // Given
        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        version.setId(versionId);

        Version parentVersion = new Version("Parent Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        parentVersion.setId(parentId);
        parentVersion.setMergeTimestamp(LocalDateTime.now().minusDays(1));

        version.setMergedFromVersion(parentVersion);
        version.setMergeTimestamp(LocalDateTime.now());

        Version sibling = new Version("Sibling Version", parentVersion, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        sibling.setId(siblingId);
        sibling.setMergeTimestamp(LocalDateTime.now().minusDays(2)); // Older than our merge

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.findByParentVersionId(parentId)).thenReturn(List.of(sibling));

        // When
        List<VersionConflictDTO> conflicts = versionConflictService.detectConflicts(versionId);

        // Then
        assertNotNull(conflicts);
        assertTrue(conflicts.isEmpty());
        verify(versionRepository).findById(versionId);
        verify(versionRepository).findByParentVersionId(parentId);
    }

    @Test
    void detectConflicts_WhenSiblingHasNewNode_ShouldDetectConflict() {
        // Given
        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        version.setId(versionId);

        Version parentVersion = new Version("Parent Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        parentVersion.setId(parentId);

        version.setMergedFromVersion(parentVersion);
        version.setMergeTimestamp(LocalDateTime.now());

        // Create a node that exists in sibling but not in version
        BaseNode newNode = new BaseNode() {
            {
                setId(UUID.randomUUID());
                setName("New Node");
                // Set other required fields...
            }
        };
        
        Version sibling = new Version("Sibling Version", parentVersion, 
                List.of(newNode), new ArrayList<>(), // Has the new node
                new ArrayList<>(), new ArrayList<>());
        sibling.setId(siblingId);
        sibling.setMergeTimestamp(LocalDateTime.now().plusHours(1)); // Newer than our merge

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(versionRepository.findByParentVersionId(parentId)).thenReturn(List.of(sibling));

        // When
        List<VersionConflictDTO> conflicts = versionConflictService.detectConflicts(versionId);

        // Then
        assertNotNull(conflicts);
        assertEquals(1, conflicts.size());
        VersionConflictDTO conflict = conflicts.get(0);
        assertEquals(siblingId, conflict.getVersionId());
        assertEquals("Sibling Version", conflict.getVersionName());
        assertEquals(1, conflict.getNodeConflicts().size());
        assertEquals(0, conflict.getConnectionConflicts().size());
        
        NodeConflictDTO nodeConflict = conflict.getNodeConflicts().get(0);
        assertEquals(newNode.getId(), nodeConflict.getNodeId());
        assertEquals("New Node", nodeConflict.getNodeName());
        assertEquals("ADDED", nodeConflict.getChangeType());
        
        verify(versionRepository).findById(versionId);
        verify(versionRepository).findByParentVersionId(parentId);
    }

    @Test
    void resolveConflict_ShouldSaveResolution() {
        // Given
        Version version = new Version("Test Version", null, new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>());
        version.setId(versionId);

        when(versionRepository.findById(versionId)).thenReturn(Optional.of(version));
        when(conflictResolutionRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // When
        versionConflictService.resolveConflict(versionId, conflictingSiblingId);

        // Then
        verify(versionRepository).findById(versionId);
        verify(conflictResolutionRepository).save(any());
        verify(logger).info("Resolved conflict for version {} with conflicting version {}", versionId, conflictingSiblingId);
    }

    @Test
    void isConflictResolved_WhenResolutionExists_ShouldReturnTrue() {
        // Given
        when(conflictResolutionRepository.existsByVersionIdAndConflictingVersionId(versionId, conflictingSiblingId))
                .thenReturn(true);

        // When
        boolean resolved = versionConflictService.isConflictResolved(versionId, conflictingSiblingId);

        // Then
        assertTrue(resolved);
        verify(conflictResolutionRepository).existsByVersionIdAndConflictingVersionId(versionId, conflictingSiblingId);
    }

    @Test
    void isConflictResolved_WhenNoResolutionExists_ShouldReturnFalse() {
        // Given
        when(conflictResolutionRepository.existsByVersionIdAndConflictingVersionId(versionId, conflictingSiblingId))
                .thenReturn(false);

        // When
        boolean resolved = versionConflictService.isConflictResolved(versionId, conflictingSiblingId);

        // Then
        assertFalse(resolved);
        verify(conflictResolutionRepository).existsByVersionIdAndConflictingVersionId(versionId, conflictingSiblingId);
    }
}
```

- [ ] **Step 2: Run tests to verify new service tests pass**

Run: `mvn test -Dtest=VersionConflictServiceTest`
Expected: All new tests should pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/org/enerscope/version/service/VersionConflictServiceTest.java
git commit -m "feat: add unit tests for conflict detection service"
```

### Task 8: Update Repository to Support New Queries

**Files:**
- Modify: `backend/src/main/java/org/enerscope/version/repository/VersionRepository.java`

**Interfaces:**
- Consumes: VersionRepository
- Produces: Additional query methods needed for conflict detection

- [ ] **Step 1: Add method to find versions by parent version ID**

Add to VersionRepository:
```java
List<Version> findByParentVersionId(UUID parentVersionId);
```

- [ ] **Step 2: Run tests to verify repository compiles**

Run: `mvn compile`
Expected: Compilation should succeed

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/org/enerscope/version/repository/VersionRepository.java
git commit -m "feat: add parent version ID query method to VersionRepository"
```

### Task 9: Integration Test for Complete Flow

**Files:**
- Modify: `backend/src/test/java/org/enerscope/version/service/VersionServiceTest.java` (add integration test)
- Or create new integration test class

**Interfaces:**
- Consumes: All new functionality
- Produces: End-to-end test of conflict detection workflow

- [ ] **Step 1: Add integration test for merge -> conflict detection -> resolution flow**

Add to VersionServiceTest.java or create new test:
```java
@Test
void mergeAndDetectConflicts_ShouldWorkEndToEnd() {
    // Given - Create parent version with initial node
    UUID parentId = UUID.randomUUID();
    UUID subId = UUID.randomUUID();
    UUID siblingSubId = UUID.randomUUID();
    
    Well parentWell = new Well(
            "Parent Well",
            NodeStateEnum.PROPOSED,
            Instant.now(),
            120,
            MoneyAmount.of(1000000),
            30,
            MoneyAmount.of(50000),
            0.0f,
            new InvestmentCost(),
            new NodeGraphData(),
            UUID.randomUUID(),
            new NodeTypeData(),
            100.0f,
            0.5f,
            0.8f,
            10,
            MoneyAmount.of(5000),
            500f);

    NodeChange parentAddChange = new NodeChange();
    parentAddChange.setChangeType(ChangeTypeEnum.ADD);
    parentAddChange.setChangedNode(parentWell);
    parentAddChange.setResultNode(parentWell);

    Version parentVersion = new Version(
            "Parent Version",
            null,
            new ArrayList<>(List.of(parentWell)),
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(List.of(parentAddChange)));

    // Create first subversion (the one we'll merge)
    Well subWell = new Well(
            "Sub Well",
            NodeStateEnum.RUNNING,
            Instant.now(),
            120,
            MoneyAmount.of(1000000),
            30,
            MoneyAmount.of(50000),
            0.0f,
            new InvestmentCost(),
            new NodeGraphData(),
            parentWell.getId(),
            new NodeTypeData(),
            100.0f,
            0.5f,
            0.8f,
            10,
            MoneyAmount.of(5000),
            500f);

    NodeChange subNoChange = new NodeChange(); // No changes in this subversion
    subNoChange.setChangeType(ChangeTypeEnum.EDIT); // Actually no change but we'll treat as edit for simplicity
    subNoChange.setChangedNode(parentWell);
    subNoChange.setResultNode(parentWell);

    Version subVersion = new Version(
            "Sub Version",
            parentVersion,
            new ArrayList<>(List.of(subWell)), // Same as parent
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(List.of(subNoChange)));

    // Create sibling subversion that adds a new node
    Well siblingNewWell = new Well(
            "New Node from Sibling",
            NodeStateEnum.PROPOSED,
            Instant.now(),
            120,
            MoneyAmount.of(1000000),
            30,
            MoneyAmount.of(50000),
            0.0f,
            new InvestmentCost(),
            new NodeGraphData(),
            UUID.randomUUID(),
            new NodeTypeData(),
            100.0f,
            0.5f,
            0.8f,
            10,
            MoneyAmount.of(5000),
            500f);

    NodeChange siblingAddChange = new NodeChange();
    siblingAddChange.setChangeType(ChangeTypeEnum.ADD);
    siblingAddChange.setChangedNode(siblingNewWell);
    siblingAddChange.setResultNode(siblingNewWell);

    Version siblingSubVersion = new Version(
            "Sibling Sub Version",
            parentVersion,
            new ArrayList<>(List.of(subWell, siblingNewWell)), // Has the extra node
            new ArrayList<>(),
            new ArrayList<>(),
            new ArrayList<>(List.of(siblingAddChange)));

    // When - Merge the subversion into parent
    when(versionRepository.findById(parentId)).thenReturn(Optional.of(parentVersion));
    when(versionRepository.findById(subId)).thenReturn(Optional.of(subVersion));
    when(versionRepository.findById(siblingSubId)).thenReturn(Optional.of(siblingSubVersion));
    when(versionRepository.save(any(Version.class))).thenAnswer(invocation -> invocation.getArgument(0));
    
    // Mock parent repository calls for version service
    when(versionRepository.findByParentVersionId(null)).thenReturn(List.of(subVersion, siblingSubVersion));
    
    Version mergedParent = versionService.mergeSubVersionIntoParent(subId);
    
    // Then - Detect conflicts in sibling subversion (should see the merge as a conflict)
    List<VersionConflictDTO> conflicts = versionConflictService.detectConflicts(siblingSubId);
    
    // The sibling should detect that the parent was modified by our merge
    assertEquals(1, conflicts.size());
    VersionConflictDTO conflict = conflicts.get(0);
    assertEquals(parentId, conflict.getVersionId());
    assertEquals("Parent Version", conflict.getVersionName());
    
    // Verify that we can resolve the conflict
    versionConflictService.resolveConflict(siblingSubId, parentId);
    
    // Verify conflict is now resolved
    boolean isResolved = versionConflictService.isConflictResolved(siblingSubId, parentId);
    assertTrue(isResolved);
}
```

- [ ] **Step 2: Run integration test**

Run: `mvn test`
Expected: All tests should pass including the new integration test

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/java/org/enerscope/version/service/VersionServiceTest.java
git commit -m "feat: add integration test for conflict detection workflow"
```

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-09-16-version-conflict-detection.md`. Two execution options:**

**1. Subagent-Driven (recommended)** - I dispatch a fresh subagent per task, review between tasks, fast iteration

**2. Inline Execution** - Execute tasks in this session using executing-plans, batch execution with checkpoints

**Which approach?**