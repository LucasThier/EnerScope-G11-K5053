package org.enerscope.user.dto;

import java.util.UUID;

public record UserSearchResultDTO(
        UUID id,
        String firstName,
        String lastName,
        String mail
) {}
