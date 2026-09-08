package org.enerscope.auth.dto;

import org.enerscope.user.dto.UserSummaryDTO;

import java.time.Instant;

public record NewSessionDTO(
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        UserSummaryDTO user
) {}
