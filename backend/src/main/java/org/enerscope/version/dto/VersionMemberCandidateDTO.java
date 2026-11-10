package org.enerscope.version.dto;

import java.util.UUID;

public record VersionMemberCandidateDTO(
        UUID id,
        String firstName,
        String lastName,
        String mail
) {}
