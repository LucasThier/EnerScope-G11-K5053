package org.enerscope.version.dto;

import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lightweight view of a version for listing the versions of a project. Does
 * not carry the node/connection snapshot — use the diagram endpoint for that.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class VersionSummaryDTO {

    private UUID id;
    private String name;
    private UUID parentVersionId;
    private Instant createdAt;
}
