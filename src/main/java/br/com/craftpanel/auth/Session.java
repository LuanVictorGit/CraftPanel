package br.com.craftpanel.auth;

import java.time.Instant;

public record Session(
        String token,
        String userId,
        String username,
        String displayName,
        String roleId,
        String ipAddress,
        String userAgent,
        Instant expiresAt,
        Instant createdAt
) {}
