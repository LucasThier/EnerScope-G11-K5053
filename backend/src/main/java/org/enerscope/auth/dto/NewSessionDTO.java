package org.enerscope.auth.dto;

import java.time.Instant;

public record NewSessionDTO(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {}
