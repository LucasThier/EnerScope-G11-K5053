package org.enerscope.project.dto;

import java.util.UUID;

public record ProjectMemberCandidateDTO(
        UUID id,
        String firstName,
        String lastName,
        String mail
) {}
