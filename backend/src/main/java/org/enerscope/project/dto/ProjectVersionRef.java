package org.enerscope.project.dto;

import java.util.UUID;

/** Id and name of a project's version; a projection so listing them never loads node snapshots. */
public record ProjectVersionRef(UUID id, String name) {}
