package org.enerscope.dto.session;

import java.time.Instant;

public record NewSessionDTO(
        String accessToken,
        String refreshToken,
        Instant expiresAt
) {}
